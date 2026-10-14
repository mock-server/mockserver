package org.mockserver.templates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.model.MediaType;
import org.mockserver.serialization.HttpRequestSerializer;
import org.mockserver.serialization.HttpResponseSerializer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.templates.engine.TemplateEvaluationException;
import org.mockserver.templates.engine.TemplateWarning;
import org.mockserver.templates.engine.helpers.ScenarioTemplateHelper;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateTimeoutException;
import org.mockserver.templates.engine.mustache.MustacheTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateEngine;
import org.mockserver.templates.engine.velocity.VelocityTemplateTimeoutException;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.TOO_MANY_REQUESTS;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.TEMPLATE_GENERATED;
import static org.mockserver.log.model.LogEntry.LogMessageType.TEMPLATE_GENERATION_FAILED;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Renders a response or forward template against a sample request without an expectation, for
 * {@code PUT /mockserver/template/test}. It uses the same engines, sandbox settings and JavaScript and
 * Velocity time limits as serving, each still applied when disabled; nothing it does reaches the event
 * log, and a {@code scenario.set(...)} is reported rather than applied. At most
 * {@link #MAX_CONCURRENT_DRY_RUNS} run at once on their own threads, never on the caller's: a Mustache
 * template, or a Velocity template inside one long Java call, cannot be stopped, so it keeps its thread
 * and later calls are refused with a 429 rather than queued.
 */
public class TemplateDryRun {

    static final int MAX_CONCURRENT_DRY_RUNS = 2;
    static final long TIMEOUT_WHEN_DISABLED_MILLIS = 5_000L;
    static final long RESPONSE_GRACE_MILLIS = 5_000L;
    static final int MAX_REPORTED_OUTPUT_CHARS = 64 * 1024;
    static final int MAX_REPORTED_MESSAGE_CHARS = 8 * 1024;
    static final int MAX_REPORTED_WARNING_CHARS = 1024;
    static final int MAX_WARNINGS = 50;
    private static final String RESPONSE_TEMPLATE = "RESPONSE_TEMPLATE";
    private static final String FORWARD_TEMPLATE = "FORWARD_TEMPLATE";

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private final CapturingLogger capturingLogger = new CapturingLogger();
    private final Configuration configuration;
    private final ThreadPoolExecutor executor;
    private final long timeoutWhenDisabledMillis;
    private final long responseGraceMillis;
    private VelocityTemplateEngine velocityTemplateEngine;
    private MustacheTemplateEngine mustacheTemplateEngine;
    private JavaScriptTemplateEngine javaScriptTemplateEngine;

    public TemplateDryRun(Configuration configuration) {
        this(configuration, TIMEOUT_WHEN_DISABLED_MILLIS, RESPONSE_GRACE_MILLIS);
    }

    TemplateDryRun(Configuration configuration, long timeoutWhenDisabledMillis, long responseGraceMillis) {
        this.configuration = configuration;
        this.timeoutWhenDisabledMillis = timeoutWhenDisabledMillis;
        this.responseGraceMillis = responseGraceMillis;
        this.executor = new ThreadPoolExecutor(0, MAX_CONCURRENT_DRY_RUNS, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "MockServer-TemplateDryRun");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * @param controlPlaneRequest the {@code PUT /mockserver/template/test} request
     * @return the response to write, completed within the template timeout plus a grace period that covers engine start-up
     */
    public CompletableFuture<HttpResponse> test(HttpRequest controlPlaneRequest) {
        DryRunInput input;
        try {
            input = parse(controlPlaneRequest);
        } catch (IllegalArgumentException invalidInput) {
            return CompletableFuture.completedFuture(jsonResponse(BAD_REQUEST.code(), putCapped(objectMapper.createObjectNode(), "error", invalidInput.getMessage(), MAX_REPORTED_MESSAGE_CHARS)));
        }
        long deadlineMillis = timeoutMillis(input.templateType()) + responseGraceMillis;
        CompletableFuture<HttpResponse> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    result.complete(evaluate(input));
                } catch (Throwable throwable) {
                    result.complete(jsonResponse(OK.code(), failedReport(input, throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName())));
                }
            });
        } catch (RejectedExecutionException busy) {
            return CompletableFuture.completedFuture(jsonResponse(TOO_MANY_REQUESTS.code(), objectMapper.createObjectNode()
                .put("error", MAX_CONCURRENT_DRY_RUNS + " template dry runs are already running; retry when one finishes (a Mustache template that never finishes keeps its slot until MockServer restarts, and a Velocity template inside one long Java call keeps it until that call returns)")));
        }
        ObjectNode timedOut = failedReport(input, "the template did not finish within " + deadlineMillis + "ms; " + stillRunning(input.templateType()));
        timedOut.put("timedOut", true);
        return result.completeOnTimeout(jsonResponse(OK.code(), timedOut), deadlineMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * The engine's own time limit; Mustache has none, so it shares the JavaScript one.
     */
    long timeoutMillis(HttpTemplate.TemplateType templateType) {
        Long configured = templateType == HttpTemplate.TemplateType.VELOCITY ? configuration.velocityTemplateExecutionTimeout() : configuration.javascriptTemplateExecutionTimeout();
        return configured != null && configured > 0 ? configured : timeoutWhenDisabledMillis;
    }

    private static String stillRunning(HttpTemplate.TemplateType templateType) {
        switch (templateType) {
            case VELOCITY:
                return "a Velocity template is stopped at its next loop iteration, macro call, #parse, #evaluate or #define block render, so one inside a long Java call may still be running";
            case MUSTACHE:
                return "a Mustache template cannot be stopped, so it may still be running";
            default:
                return "it may still be running until the JavaScript engine cancels it";
        }
    }

    private DryRunInput parse(HttpRequest controlPlaneRequest) {
        String body = controlPlaneRequest.getBodyAsJsonOrXmlString();
        if (isBlank(body)) {
            throw new IllegalArgumentException("a body is required: {\"templateType\": \"VELOCITY | MUSTACHE | JAVASCRIPT\", \"template\": \"...\", \"request\": {...}}");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(body);
        } catch (Exception notJson) {
            throw new IllegalArgumentException("the body is not valid JSON: " + notJson.getMessage());
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("the body must be a JSON object");
        }
        HttpTemplate.TemplateType templateType;
        try {
            templateType = HttpTemplate.TemplateType.valueOf(node.path("templateType").asText("").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownType) {
            throw new IllegalArgumentException("templateType must be VELOCITY, MUSTACHE or JAVASCRIPT");
        }
        JsonNode templateNode = node.get("template");
        if (templateNode == null || !templateNode.isTextual() || isBlank(templateNode.asText())) {
            throw new IllegalArgumentException("template is required, as a string");
        }
        String actionType = node.path("actionType").asText(RESPONSE_TEMPLATE).trim().toUpperCase(Locale.ROOT);
        if (!RESPONSE_TEMPLATE.equals(actionType) && !FORWARD_TEMPLATE.equals(actionType)) {
            throw new IllegalArgumentException("actionType must be RESPONSE_TEMPLATE (the default) or FORWARD_TEMPLATE");
        }
        HttpRequest sampleRequest = request().withMethod("GET").withPath("/");
        JsonNode requestNode = node.get("request");
        if (requestNode != null && !requestNode.isNull()) {
            try {
                sampleRequest = new HttpRequestSerializer(capturingLogger).deserialize(requestNode.toString());
            } catch (RuntimeException invalidRequest) {
                throw new IllegalArgumentException("request is not a valid request: " + invalidRequest.getMessage());
            }
        }
        return new DryRunInput(templateType, templateNode.asText(), FORWARD_TEMPLATE.equals(actionType), sampleRequest);
    }

    private HttpResponse evaluate(DryRunInput input) throws Exception {
        List<LogEntry> logEntries = new ArrayList<>();
        Set<TemplateWarning> warnings = new LinkedHashSet<>();
        long start = System.nanoTime();
        Object rendered = null;
        Throwable failure = null;
        capturingLogger.capture(logEntries);
        try {
            rendered = ScenarioTemplateHelper.withoutStateChanges(
                (name, state) -> addWarning(warnings, new TemplateWarning("scenario.set(\"" + name + "\", \"" + state + "\") was not applied: a dry run does not change scenario state", null, null)),
                () -> render(input, warning -> addWarning(warnings, warning))
            );
        } catch (Throwable throwable) {
            failure = throwable;
        } finally {
            capturingLogger.stopCapture();
        }
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        ObjectNode report = report(input);
        report.put("durationMillis", durationMillis);
        String renderedOutput = null;
        LogEntry generationFailure = null;
        for (LogEntry logEntry : logEntries) {
            if (logEntry.getType() == TEMPLATE_GENERATED && logEntry.getArguments() != null && logEntry.getArguments().length > 0) {
                renderedOutput = renderedText(logEntry.getArguments()[0]);
            } else if (logEntry.getType() == TEMPLATE_GENERATION_FAILED && generationFailure == null) {
                generationFailure = logEntry;
            } else if (logEntry.getLogLevel() == Level.WARN && failure == null && isNotBlankMessage(logEntry)) {
                addWarning(warnings, new TemplateWarning(logEntry.getMessage(), null, null));
            }
        }

        if (failure != null) {
            report.put("valid", false);
            report.set("error", errorNode(failure));
            if (failure instanceof JavaScriptTemplateTimeoutException || failure instanceof VelocityTemplateTimeoutException) {
                report.put("timedOut", true);
            }
        } else if (rendered == null) {
            report.put("valid", false);
            ObjectNode error = report.putObject("error");
            String servedStatus = Boolean.TRUE.equals(configuration.templateFailureAsServerError()) ? "500" : input.forward() ? "502" : "404";
            error.put("message", "the template rendered output that is not a valid " + (input.forward() ? "request" : "response")
                + ", so serving it would answer with a " + servedStatus);
            if (generationFailure != null && generationFailure.getArguments() != null && generationFailure.getArguments().length > 1) {
                putCapped(error, "validationErrors", String.valueOf(generationFailure.getArguments()[0]), MAX_REPORTED_MESSAGE_CHARS);
                renderedOutput = String.valueOf(generationFailure.getArguments()[1]);
            }
        } else {
            report.put("valid", true);
            String renderedJson = rendered instanceof HttpResponse
                ? new HttpResponseSerializer(capturingLogger).serialize((HttpResponse) rendered)
                : new HttpRequestSerializer(capturingLogger).serialize((HttpRequest) rendered);
            if (renderedJson.length() <= MAX_REPORTED_OUTPUT_CHARS) {
                report.set(input.forward() ? "renderedRequest" : "renderedResponse", objectMapper.readTree(renderedJson));
                renderedOutput = null;
            }
        }
        if (renderedOutput != null) {
            if (renderedOutput.length() > MAX_REPORTED_OUTPUT_CHARS) {
                report.put("renderedOutput", renderedOutput.substring(0, MAX_REPORTED_OUTPUT_CHARS));
                report.put("renderedOutputTruncated", true);
            } else {
                report.put("renderedOutput", renderedOutput);
            }
        }
        if (!warnings.isEmpty()) {
            ArrayNode warningsNode = report.putArray("warnings");
            warnings.forEach(warning -> {
                ObjectNode warningNode = putCapped(warningsNode.addObject(), "message", warning.message(), MAX_REPORTED_WARNING_CHARS);
                if (warning.line() != null) {
                    warningNode.put("line", warning.line());
                }
                if (warning.column() != null) {
                    warningNode.put("column", warning.column());
                }
            });
        }
        return jsonResponse(OK.code(), report);
    }

    private static void addWarning(Set<TemplateWarning> warnings, TemplateWarning warning) {
        if (warnings.size() < MAX_WARNINGS) {
            warnings.add(warning);
        }
    }

    private Object render(DryRunInput input, java.util.function.Consumer<TemplateWarning> warnings) {
        switch (input.templateType()) {
            case VELOCITY:
                return input.forward()
                    ? velocity().executeTemplate(input.template(), input.sampleRequest(), HttpRequestDTO.class, warnings)
                    : velocity().executeTemplate(input.template(), input.sampleRequest(), HttpResponseDTO.class, warnings);
            case MUSTACHE:
                return input.forward()
                    ? mustache().executeTemplate(input.template(), input.sampleRequest(), HttpRequestDTO.class, warnings)
                    : mustache().executeTemplate(input.template(), input.sampleRequest(), HttpResponseDTO.class, warnings);
            case JAVASCRIPT:
                return input.forward()
                    ? javaScript().executeTemplate(input.template(), input.sampleRequest(), HttpRequestDTO.class)
                    : javaScript().executeTemplate(input.template(), input.sampleRequest(), HttpResponseDTO.class);
            default:
                throw new IllegalArgumentException("unsupported template type " + input.templateType());
        }
    }

    private ObjectNode errorNode(Throwable failure) {
        ObjectNode error = objectMapper.createObjectNode();
        if (failure instanceof TemplateEvaluationException evaluationFailure) {
            Throwable cause = evaluationFailure.getCause();
            putCapped(error, "message", cause != null && cause.getMessage() != null ? cause.getMessage() : String.valueOf(cause), MAX_REPORTED_MESSAGE_CHARS);
            if (evaluationFailure.getLine() != null) {
                error.put("line", evaluationFailure.getLine());
            }
            if (evaluationFailure.getColumn() != null) {
                error.put("column", evaluationFailure.getColumn());
            }
            if (evaluationFailure.getHint() != null) {
                putCapped(error, "hint", evaluationFailure.getHint(), MAX_REPORTED_MESSAGE_CHARS);
            }
        } else {
            putCapped(error, "message", failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName(), MAX_REPORTED_MESSAGE_CHARS);
        }
        return error;
    }

    private ObjectNode report(DryRunInput input) {
        return objectMapper.createObjectNode()
            .put("templateType", input.templateType().name())
            .put("actionType", input.forward() ? FORWARD_TEMPLATE : RESPONSE_TEMPLATE);
    }

    /**
     * Engine messages quote the template and whatever it threw, so every message is cut to {@code maxChars},
     * with {@code <field>Truncated} set, to keep the report's size bounded.
     */
    private static ObjectNode putCapped(ObjectNode node, String field, String value, int maxChars) {
        if (value != null && value.length() > maxChars) {
            node.put(field, value.substring(0, maxChars));
            node.put(field + "Truncated", true);
        } else {
            node.put(field, value);
        }
        return node;
    }

    private ObjectNode failedReport(DryRunInput input, String message) {
        ObjectNode report = report(input).put("valid", false);
        putCapped(report.putObject("error"), "message", message, MAX_REPORTED_MESSAGE_CHARS);
        return report;
    }

    private String renderedText(Object generated) {
        if (generated instanceof JsonNode) {
            try {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(generated);
            } catch (Exception ignored) {
                // fall through to its plain text
            }
        }
        return String.valueOf(generated);
    }

    private static boolean isNotBlankMessage(LogEntry logEntry) {
        return !isBlank(logEntry.getMessageFormat());
    }

    private HttpResponse jsonResponse(int statusCode, ObjectNode body) {
        String json;
        try {
            json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
        } catch (Exception e) {
            json = body.toString();
        }
        return response().withStatusCode(statusCode).withBody(json, MediaType.JSON_UTF_8);
    }

    private synchronized VelocityTemplateEngine velocity() {
        if (velocityTemplateEngine == null) {
            velocityTemplateEngine = capturingLogger.withoutCapture(() -> VelocityTemplateEngine.forDryRun(capturingLogger, configuration, timeoutWhenDisabledMillis));
        }
        return velocityTemplateEngine;
    }

    private synchronized MustacheTemplateEngine mustache() {
        if (mustacheTemplateEngine == null) {
            mustacheTemplateEngine = capturingLogger.withoutCapture(() -> new MustacheTemplateEngine(capturingLogger, configuration));
        }
        return mustacheTemplateEngine;
    }

    private synchronized JavaScriptTemplateEngine javaScript() {
        if (javaScriptTemplateEngine == null) {
            javaScriptTemplateEngine = capturingLogger.withoutCapture(() -> JavaScriptTemplateEngine.forDryRun(capturingLogger, configuration, timeoutWhenDisabledMillis));
        }
        return javaScriptTemplateEngine;
    }

    private record DryRunInput(HttpTemplate.TemplateType templateType, String template, boolean forward, HttpRequest sampleRequest) {
    }

    /**
     * Keeps a dry run's log entries out of the event log and the server log: while a dry run renders on a
     * thread they are collected for its report, and at any other time they are dropped.
     */
    private static final class CapturingLogger extends MockServerLogger {

        private static final int MAX_CAPTURED_ENTRIES = 200;
        private final ThreadLocal<List<LogEntry>> sink = new ThreadLocal<>();

        private CapturingLogger() {
            super(TemplateDryRun.class);
        }

        void capture(List<LogEntry> logEntries) {
            sink.set(logEntries);
        }

        void stopCapture() {
            sink.remove();
        }

        <T> T withoutCapture(java.util.function.Supplier<T> action) {
            List<LogEntry> previous = sink.get();
            sink.remove();
            try {
                return action.get();
            } finally {
                if (previous != null) {
                    sink.set(previous);
                }
            }
        }

        @Override
        public void logEvent(LogEntry logEntry) {
            List<LogEntry> logEntries = sink.get();
            if (logEntries != null && (logEntries.size() < MAX_CAPTURED_ENTRIES || logEntry.getType() == TEMPLATE_GENERATED || logEntry.getType() == TEMPLATE_GENERATION_FAILED)) {
                logEntries.add(logEntry);
            }
        }

        @Override
        public boolean isEnabledForInstance(Level level) {
            return sink.get() != null;
        }
    }
}
