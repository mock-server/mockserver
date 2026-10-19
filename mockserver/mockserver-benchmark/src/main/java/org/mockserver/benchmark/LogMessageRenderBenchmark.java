package org.mockserver.benchmark;

import org.mockserver.configuration.Configuration;
import org.mockserver.formatting.StringFormatter;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.NOPLogger;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

/**
 * Cost of rendering one event-log message as the console does ({@link StringFormatter#formatLogMessage}),
 * against the renderings it replaced, in one JMH run. The {@code indent*} pair isolates the indent of an
 * already-serialised argument. {@code messageLoop} streams each JSON argument into the message;
 * {@code messageToString} is a copy of the rendering row 126a left, which builds each argument's Jackson
 * {@code toString} and then indents it with a loop; and {@code messageRegex} indents with the {@code (?m)^} regex. The {@code console*} pair is the consumer's
 * console write of the same entry: to a logger that prints nothing, which no longer renders the message, and to
 * a logger that takes the rendered text.
 *
 * <pre>./run.sh -prof gc LogMessageRenderBenchmark</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class LogMessageRenderBenchmark {

    private static final Pattern START_OF_EACH_LINE = Pattern.compile("(?m)^");
    private static final Pattern ARGUMENT_PLACEHOLDER = Pattern.compile("\\{}");

    /**
     * RECEIVED: "received request:{}"; NOT_MATCHED: a did-not-match entry quoting the request, the expectation and
     * the because text; RESPONSE: "returning response:{}for request:{}for action:{}from expectation:{}".
     */
    @Param({"RECEIVED", "NOT_MATCHED", "RESPONSE"})
    private String shape;

    /**
     * SIMPLE: the bodiless keep-alive GET the perf sweep sends; JSON: a POST with a 1 KB pretty-printed JSON body.
     */
    @Param({"SIMPLE", "JSON"})
    private String request;

    private String format;
    private Object[] arguments;
    private String serialisedRequest;
    private LogEntry logEntry;
    private Configuration configuration;
    private Logger renderingLogger;

    @Setup
    public void setup() {
        Set<String> methods = new HashSet<>();
        for (Method method : StringFormatter.class.getDeclaredMethods()) {
            methods.add(method.getName());
        }
        if (!methods.contains("appendIndented") || !methods.contains("appendFormattedArgument")) {
            throw new IllegalStateException("mockserver-core on the classpath predates the indent loop or the streamed JSON arguments,"
                + " so messageLoop would measure an older rendering; put this tree's mockserver-core first on the classpath");
        }
        HttpRequest httpRequest = "SIMPLE".equals(request)
            ? request("/simple").withMethod("GET").withHeader(header("Host", "localhost:1080")).withHeader(header("User-Agent", "k6/0.54.0")).withKeepAlive(true).withSecure(false)
            : request("/api/orders").withMethod("POST").withHeader(header("Content-Type", "application/json")).withBody(json(jsonBody()));
        HttpResponse httpResponse = response().withStatusCode(200).withBody("{\"id\":42}");
        Expectation expectation = new Expectation(request("/other").withMethod("GET")).withId("expectation-1").thenRespond(httpResponse);
        switch (shape) {
            case "RECEIVED":
                arguments = new Object[]{httpRequest};
                logEntry = new LogEntry().setMessageFormat("received request:{}");
                break;
            case "NOT_MATCHED":
                arguments = new Object[]{httpRequest, expectation, "method matched" + NEW_LINE + "  path didn't match"};
                logEntry = new LogEntry().setMessageFormat("request:{}didn't match expectation \"expectation-1\":{}because:{}");
                break;
            default:
                arguments = new Object[]{httpResponse, httpRequest, httpResponse, "expectation-1"};
                logEntry = new LogEntry().setMessageFormat("returning response:{}for request:{}for action:{}from expectation:{}");
        }
        format = logEntry.getMessageFormat();
        logEntry.setLogLevel(Level.INFO).setArguments(arguments);
        serialisedRequest = String.valueOf(httpRequest);
        if (!messageLoop().equals(messageRegex()) || !messageLoop().equals(messageToString())) {
            throw new IllegalStateException("the renderings differ");
        }
        configuration = Configuration.configuration().logLevel(Level.INFO).compactLogFormat(false);
        renderingLogger = new RenderingLogger();
    }

    @Benchmark
    public StringBuilder[] indentLoop() {
        return StringFormatter.indentAndToString(1, new Object[]{serialisedRequest});
    }

    @Benchmark
    public StringBuilder indentRegex() {
        return regexIndentAndToString(1, serialisedRequest);
    }

    @Benchmark
    public String messageLoop() {
        return StringFormatter.formatLogMessage(format, arguments);
    }

    @Benchmark
    public String messageToString() {
        final StringBuilder logMessage = new StringBuilder();
        final String[] messageParts = ARGUMENT_PLACEHOLDER.split(format);
        final StringBuilder[] formattedArguments = new StringBuilder[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            final String text = String.valueOf(arguments[i]);
            final StringBuilder indented = new StringBuilder(text.length() + (text.length() >> 3) + 16);
            indented.append(NEW_LINE).append(NEW_LINE);
            loopIndent(indented, text, "  ");
            formattedArguments[i] = indented.append(NEW_LINE);
        }
        for (int messagePartIndex = 0; messagePartIndex < messageParts.length; messagePartIndex++) {
            logMessage.append(messageParts[messagePartIndex]);
            if (formattedArguments.length > messagePartIndex) {
                logMessage.append(formattedArguments[messagePartIndex]);
            }
            if (messagePartIndex < messageParts.length - 1) {
                logMessage.append(NEW_LINE);
                if (!messageParts[messagePartIndex + 1].startsWith(" ")) {
                    logMessage.append(" ");
                }
            }
        }
        return logMessage.toString();
    }

    // StringFormatter's indent loop as row 126a left it
    private static void loopIndent(final StringBuilder target, final String text, final String indentation) {
        final int length = text.length();
        if (length == 0) {
            return;
        }
        target.append(indentation);
        int lineStart = 0;
        for (int i = 0; i < length; i++) {
            final char c = text.charAt(i);
            if ((c <= '\r' || c >= '\u0085') && (c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029')) {
                if (c == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (i + 1 < length) {
                    target.append(text, lineStart, i + 1).append(indentation);
                    lineStart = i + 1;
                }
            }
        }
        target.append(text, lineStart, length);
    }

    @Benchmark
    public void consoleDiscarded() {
        MockServerLogger.writeToSystemOut(NOPLogger.NOP_LOGGER, logEntry, configuration);
    }

    @Benchmark
    public int consoleRendered() {
        MockServerLogger.writeToSystemOut(renderingLogger, logEntry, configuration);
        return ((RenderingLogger) renderingLogger).characters;
    }

    @Benchmark
    public String messageRegex() {
        final StringBuilder logMessage = new StringBuilder();
        final String[] messageParts = ARGUMENT_PLACEHOLDER.split(format);
        final StringBuilder[] formattedArguments = new StringBuilder[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            formattedArguments[i] = regexIndentAndToString(1, arguments[i]);
        }
        for (int messagePartIndex = 0; messagePartIndex < messageParts.length; messagePartIndex++) {
            logMessage.append(messageParts[messagePartIndex]);
            if (formattedArguments.length > messagePartIndex) {
                logMessage.append(formattedArguments[messagePartIndex]);
            }
            if (messagePartIndex < messageParts.length - 1) {
                logMessage.append(NEW_LINE);
                if (!messageParts[messagePartIndex + 1].startsWith(" ")) {
                    logMessage.append(" ");
                }
            }
        }
        return logMessage.toString();
    }

    private static StringBuilder regexIndentAndToString(int indent, Object argument) {
        return new StringBuilder(NEW_LINE)
            .append(NEW_LINE)
            .append(START_OF_EACH_LINE.matcher(String.valueOf(argument)).replaceAll(indent == 1 ? "  " : "    "))
            .append(NEW_LINE);
    }

    // takes every message, as a logger with a handler does, and prints none of them
    private static final class RenderingLogger extends LegacyAbstractLogger {
        private int characters;

        private RenderingLogger() {
            this.name = "rendering";
        }

        @Override
        protected String getFullyQualifiedCallerName() {
            return null;
        }

        @Override
        protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable) {
            characters += messagePattern.length();
        }

        @Override
        public boolean isTraceEnabled() {
            return true;
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }
    }

    private static String jsonBody() {
        StringBuilder body = new StringBuilder("{\n  \"items\" : [");
        for (int i = 0; i < 16; i++) {
            body.append(i == 0 ? "" : ",").append("\n    { \"id\" : ").append(i).append(", \"name\" : \"item-").append(i).append("\", \"price\" : ").append(i * 3).append(" }");
        }
        return body.append("\n  ]\n}").toString();
    }
}
