package org.mockserver.mock;

import com.google.common.base.Joiner;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.formatting.StringFormatter;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.memory.MemoryMonitoring;
import org.mockserver.model.*;
import org.mockserver.persistence.ExpectationFileSystemPersistence;
import org.mockserver.persistence.ExpectationFileWatcher;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.*;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.serialization.java.HttpRequestToJavaSerializer;
import org.mockserver.server.initialize.ExpectationInitializerLoader;
import org.mockserver.ui.MockServerMatcherNotifier.Cause;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.addSubjectAlternativeName;
import static org.mockserver.configuration.ConfigurationProperties.maxFutureTimeout;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
public class HttpStateHandler {

    public static final String LOG_SEPARATOR = NEW_LINE + "------------------------------------" + NEW_LINE;
    public static final String PATH_PREFIX = "/mockserver";
    private final String uniqueLoopPreventionHeaderValue = "MockServer_" + UUID.randomUUID().toString();
    private final MockServerEventLog mockServerLog;
    private final Scheduler scheduler;
    private ExpectationFileSystemPersistence expectationFileSystemPersistence;
    private ExpectationFileWatcher expectationFileWatcher;
    // mockserver
    private RequestMatchers requestMatchers;
    private MockServerLogger mockServerLogger;
    private WebSocketClientRegistry webSocketClientRegistry;
    // serializers
    private HttpRequestSerializer httpRequestSerializer;
    private LogEventRequestAndResponseSerializer httpRequestResponseSerializer;
    private ExpectationSerializer expectationSerializer;
    private HttpRequestToJavaSerializer httpRequestToJavaSerializer;
    private ExpectationToJavaSerializer expectationToJavaSerializer;
    private VerificationSerializer verificationSerializer;
    private VerificationSequenceSerializer verificationSequenceSerializer;
    private LogEntrySerializer logEntrySerializer;
    private MemoryMonitoring memoryMonitoring;
    private OpenAPIV3Parser openAPIV3Parser = new OpenAPIV3Parser();

    public HttpStateHandler(MockServerLogger mockServerLogger, Scheduler scheduler) {
        this.mockServerLogger = mockServerLogger.setHttpStateHandler(this);
        this.scheduler = scheduler;
        this.webSocketClientRegistry = new WebSocketClientRegistry(mockServerLogger);
        this.mockServerLog = new MockServerEventLog(mockServerLogger, scheduler, true);
        this.requestMatchers = new RequestMatchers(mockServerLogger, scheduler, webSocketClientRegistry);
        if (ConfigurationProperties.persistExpectations()) {
            this.expectationFileSystemPersistence = new ExpectationFileSystemPersistence(mockServerLogger, requestMatchers);
        }
        if (ConfigurationProperties.watchInitializationJson()) {
            this.expectationFileWatcher = new ExpectationFileWatcher(mockServerLogger, requestMatchers);
        }
        this.memoryMonitoring = new MemoryMonitoring(this.mockServerLog, this.requestMatchers);
        new ExpectationInitializerLoader(mockServerLogger, requestMatchers);
    }

    public MockServerLogger getMockServerLogger() {
        return mockServerLogger;
    }

    public void clear(HttpRequest request) {
        HttpRequest requestMatcher = null;
        if (isNotBlank(request.getBodyAsString())) {
            requestMatcher = getHttpRequestSerializer().deserialize(request.getBodyAsString());
        }
        try {
            ClearType type = ClearType.valueOf(defaultIfEmpty(request.getFirstQueryStringParameter("type").toUpperCase(), "ALL"));
            switch (type) {
                case LOG:
                    mockServerLog.clear(requestMatcher);
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(CLEARED)
                            .setLogLevel(Level.INFO)
                            .setHttpRequest(requestMatcher)
                            .setMessageFormat("clearing logs that match:{}")
                            .setArguments((requestMatcher == null ? "{}" : requestMatcher))
                    );
                    break;
                case EXPECTATIONS:
                    requestMatchers.clear(requestMatcher);
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(CLEARED)
                            .setLogLevel(Level.INFO)
                            .setHttpRequest(requestMatcher)
                            .setMessageFormat("clearing expectations that match:{}")
                            .setArguments((requestMatcher == null ? "{}" : requestMatcher))
                    );
                    break;
                case ALL:
                    mockServerLog.clear(requestMatcher);
                    requestMatchers.clear(requestMatcher);
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(CLEARED)
                            .setLogLevel(Level.INFO)
                            .setHttpRequest(requestMatcher)
                            .setMessageFormat("clearing expectations and logs that match:{}")
                            .setArguments((requestMatcher == null ? "{}" : requestMatcher))
                    );
                    break;
            }
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("\"" + request.getFirstQueryStringParameter("type") + "\" is not a valid value for \"type\" parameter, only the following values are supported " + Arrays.stream(ClearType.values()).map(input -> input.name().toLowerCase()).collect(Collectors.toList()));
        }
        System.gc();
    }

    public void reset() {
        requestMatchers.reset();
        mockServerLog.reset();
        webSocketClientRegistry.reset();
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(CLEARED)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request())
                .setMessageFormat("resetting all expectations and request logs")
        );
        System.gc();
        new Thread(() -> {
            try {
                SECONDS.sleep(10);
                memoryMonitoring.logMemoryMetrics();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        });
    }

    public List<Expectation> add(OpenAPI openAPI) {
        return new ArrayList<>();
    }

    public List<Expectation> add(Expectation... expectations) {
        List<Expectation> upsertedExpectations = new ArrayList<>();
        for (Expectation expectation : expectations) {
            if (expectation.getHttpRequest() != null) {
                final String hostHeader = expectation.getHttpRequest().getFirstHeader(HOST.toString());
                if (isNotBlank(hostHeader)) {
                    scheduler.submit(() -> addSubjectAlternativeName(hostHeader));
                }
            }
            upsertedExpectations.add(requestMatchers.add(expectation, Cause.API));
        }
        return upsertedExpectations;
    }

    public Expectation firstMatchingExpectation(HttpRequest request) {
        if (requestMatchers.isEmpty()) {
            return null;
        } else {
            return requestMatchers.firstMatchingExpectation(request);
        }
    }

    public void postProcess(Expectation expectation) {
        requestMatchers.postProcess(expectation);
    }

    public void log(LogEntry logEntry) {
        if (mockServerLog != null) {
            mockServerLog.add(logEntry);
        }
    }

    public HttpResponse retrieve(HttpRequest request) {
        CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();
        HttpResponse response = response().withStatusCode(OK.code());
        if (request != null) {
            try {
                final HttpRequest httpRequest = isNotBlank(request.getBodyAsString()) ? getHttpRequestSerializer().deserialize(request.getBodyAsString()) : null;
                Format format = Format.valueOf(defaultIfEmpty(request.getFirstQueryStringParameter("format").toUpperCase(), "JSON"));
                RetrieveType type = RetrieveType.valueOf(defaultIfEmpty(request.getFirstQueryStringParameter("type").toUpperCase(), "REQUESTS"));
                switch (type) {
                    case LOGS: {
                        final Object[] arguments = new Object[]{(httpRequest == null ? request() : httpRequest)};
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(RETRIEVED)
                                .setLogLevel(Level.INFO)
                                .setHttpRequest(httpRequest)
                                .setMessageFormat("retrieving logs that match:{}")
                                .setArguments(arguments)
                        );
                        mockServerLog.retrieveMessageLogEntries(httpRequest, (List<LogEntry> logEntries) -> {
                            StringBuilder stringBuffer = new StringBuilder();
                            for (int i = 0; i < logEntries.size(); i++) {
                                LogEntry messageLogEntry = logEntries.get(i);
                                stringBuffer
                                    .append(messageLogEntry.getTimestamp())
                                    .append(" - ")
                                    .append(messageLogEntry.getMessage());
                                if (i < logEntries.size() - 1) {
                                    stringBuffer.append(LOG_SEPARATOR);
                                }
                            }
                            stringBuffer.append(NEW_LINE);
                            response.withBody(stringBuffer.toString(), MediaType.PLAIN_TEXT_UTF_8);
                            httpResponseFuture.complete(response);
                        });
                        break;
                    }
                    case REQUESTS: {
                        final Object[] arguments = new Object[]{(httpRequest == null ? request() : httpRequest)};
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(RETRIEVED)
                                .setLogLevel(Level.INFO)
                                .setHttpRequest(httpRequest)
                                .setMessageFormat("retrieving requests in " + format.name().toLowerCase() + " that match:{}")
                                .setArguments(arguments)
                        );
                        switch (format) {
                            case JAVA:
                                mockServerLog
                                    .retrieveRequests(
                                        httpRequest,
                                        requests -> {
                                            response.withBody(
                                                getHttpRequestToJavaSerializer().serialize(requests),
                                                MediaType.create("application", "java").withCharset(UTF_8)
                                            );
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                            case JSON:
                                mockServerLog
                                    .retrieveRequests(
                                        httpRequest,
                                        requests -> {
                                            response.withBody(
                                                getHttpRequestSerializer().serialize(requests),
                                                MediaType.JSON_UTF_8
                                            );
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                            case LOG_ENTRIES:
                                mockServerLog
                                    .retrieveRequestLogEntries(
                                        httpRequest,
                                        logEntries -> {
                                            response.withBody(
                                                getLogEntrySerializer().serialize(logEntries),
                                                MediaType.JSON_UTF_8
                                            );
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                        }
                        break;
                    }
                    case REQUEST_RESPONSES: {
                        final Object[] arguments = new Object[]{(httpRequest == null ? request() : httpRequest)};
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(RETRIEVED)
                                .setLogLevel(Level.INFO)
                                .setHttpRequest(httpRequest)
                                .setMessageFormat("retrieving requests and responses in " + format.name().toLowerCase() + " that match:{}")
                                .setArguments(arguments)
                        );
                        switch (format) {
                            case JAVA:
                                response.withBody("JAVA not supported for REQUEST_RESPONSES", MediaType.create("text", "plain").withCharset(UTF_8));
                                httpResponseFuture.complete(response);
                                break;
                            case JSON:
                                mockServerLog
                                    .retrieveRequestResponses(
                                        httpRequest,
                                        httpRequestAndHttpResponses -> {
                                            response.withBody(
                                                getHttpRequestResponseSerializer().serialize(httpRequestAndHttpResponses),
                                                MediaType.JSON_UTF_8
                                            );
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                            case LOG_ENTRIES:
                                mockServerLog
                                    .retrieveRequestResponseMessageLogEntries(
                                        httpRequest,
                                        logEntries -> {
                                            response.withBody(
                                                getLogEntrySerializer().serialize(logEntries),
                                                MediaType.JSON_UTF_8
                                            );
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                        }
                        break;
                    }
                    case RECORDED_EXPECTATIONS: {
                        final Object[] arguments = new Object[]{(httpRequest == null ? request() : httpRequest)};
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(RETRIEVED)
                                .setLogLevel(Level.INFO)
                                .setHttpRequest(httpRequest)
                                .setMessageFormat("retrieving recorded expectations in " + format.name().toLowerCase() + " that match:{}")
                                .setArguments(arguments)
                        );
                        switch (format) {
                            case JAVA:
                                mockServerLog
                                    .retrieveRecordedExpectations(
                                        httpRequest,
                                        requests -> {
                                            response.withBody(
                                                getExpectationToJavaSerializer().serialize(requests),
                                                MediaType.create("application", "java").withCharset(UTF_8)
                                            );
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                            case JSON:
                                mockServerLog
                                    .retrieveRecordedExpectations(
                                        httpRequest,
                                        requests -> {
                                            response.withBody(
                                                getExpectationSerializer().serialize(requests),
                                                MediaType.JSON_UTF_8
                                            );
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                            case LOG_ENTRIES:
                                mockServerLog
                                    .retrieveRecordedExpectationLogEntries(
                                        httpRequest,
                                        logEntries -> {
                                            response.withBody(
                                                getLogEntrySerializer().serialize(logEntries),
                                                MediaType.JSON_UTF_8
                                            );
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                        }
                        break;
                    }
                    case ACTIVE_EXPECTATIONS: {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(RETRIEVED)
                                .setLogLevel(Level.INFO)
                                .setHttpRequest(httpRequest)
                                .setMessageFormat("retrieving active expectations in " + format.name().toLowerCase() + " that match:{}")
                                .setArguments(httpRequest == null ? request() : httpRequest)
                        );
                        List<Expectation> expectations = requestMatchers.retrieveActiveExpectations(httpRequest);
                        switch (format) {
                            case JAVA:
                                response.withBody(getExpectationToJavaSerializer().serialize(expectations), MediaType.create("application", "java").withCharset(UTF_8));
                                break;
                            case JSON:
                                response.withBody(getExpectationSerializer().serialize(expectations), MediaType.JSON_UTF_8);
                                break;
                            case LOG_ENTRIES:
                                response.withBody("LOG_ENTRIES not supported for ACTIVE_EXPECTATIONS", MediaType.create("text", "plain").withCharset(UTF_8));
                                break;
                        }
                        httpResponseFuture.complete(response);
                        break;
                    }
                }

                try {
                    return httpResponseFuture.get(maxFutureTimeout(), MILLISECONDS);
                } catch (ExecutionException | InterruptedException | TimeoutException e) {
                    throw new RuntimeException("Exception retrieving state for " + request, e);
                }
            } catch (IllegalArgumentException iae) {
                if (iae.getMessage().contains(RetrieveType.class.getSimpleName())) {
                    throw new IllegalArgumentException("\"" + request.getFirstQueryStringParameter("type") + "\" is not a valid value for \"type\" parameter, only the following values are supported " + Arrays.stream(RetrieveType.values()).map(input -> input.name().toLowerCase()).collect(Collectors.toList()));
                } else {
                    throw new IllegalArgumentException("\"" + request.getFirstQueryStringParameter("format") + "\" is not a valid value for \"format\" parameter, only the following values are supported " + Arrays.stream(Format.values()).map(input -> input.name().toLowerCase()).collect(Collectors.toList()));
                }
            }
        } else {
            return response().withStatusCode(200);
        }
    }

    public Future<String> verify(Verification verification) {
        CompletableFuture<String> result = new CompletableFuture<>();
        verify(verification, result::complete);
        return result;
    }

    public void verify(Verification verification, Consumer<String> resultConsumer) {
        mockServerLog.verify(verification, resultConsumer);
    }

    public Future<String> verify(VerificationSequence verification) {
        CompletableFuture<String> result = new CompletableFuture<>();
        verify(verification, result::complete);
        return result;
    }

    public void verify(VerificationSequence verification, Consumer<String> resultConsumer) {
        mockServerLog.verify(verification, resultConsumer);
    }

    public boolean handle(HttpRequest request, ResponseWriter responseWriter, boolean warDeployment) {

        if (MockServerLogger.isEnabled(TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(TRACE)
                    .setHttpRequest(request)
                    .setMessageFormat("received request:{}")
                    .setArguments(request)
            );
        }

        if (request.matches("PUT")) {

            CompletableFuture<Boolean> canHandle = new CompletableFuture<>();

            if (request.matches("PUT", PATH_PREFIX + "/expectation", "/expectation")) {

                List<Expectation> upsertedExpectations = new ArrayList<>();
                for (Expectation expectation : getExpectationSerializer().deserializeArray(request.getBodyAsString(), false)) {
                    if (!warDeployment || validateSupportedFeatures(expectation, request, responseWriter)) {
                        upsertedExpectations.addAll(add(expectation));
                    }
                }

                responseWriter.writeResponse(request, response()
                    .withStatusCode(CREATED.code())
                    .withBody(getExpectationSerializer().serialize(upsertedExpectations), MediaType.JSON_UTF_8), true);
                canHandle.complete(true);

            } else if (request.matches("PUT", PATH_PREFIX + "/openapi", "/openapi")) {

                SwaggerParseResult swaggerParseResult = getOpenAPIV3Parser().readContents(request.getBodyAsString());
                if (swaggerParseResult.getMessages().isEmpty()) {
                    List<Expectation> upsertedExpectations = new ArrayList<>(add(swaggerParseResult.getOpenAPI()));
                    responseWriter.writeResponse(request, response()
                        .withStatusCode(CREATED.code())
                        .withBody(getExpectationSerializer().serialize(upsertedExpectations), MediaType.JSON_UTF_8), true);
                } else {
                    String errorMessage = StringFormatter.formatLogMessage("Error" + (swaggerParseResult.getMessages().size() > 1 ? "s" : "") + " parsing OpenAPI specification:{}", Joiner.on("\n").join(swaggerParseResult.getMessages()));
                    responseWriter.writeResponse(request, BAD_REQUEST, errorMessage, MediaType.create("text", "plain").toString());
                }
                canHandle.complete(true);

            } else if (request.matches("PUT", PATH_PREFIX + "/clear", "/clear")) {

                clear(request);
                responseWriter.writeResponse(request, OK);
                canHandle.complete(true);

            } else if (request.matches("PUT", PATH_PREFIX + "/reset", "/reset")) {

                reset();
                responseWriter.writeResponse(request, OK);
                canHandle.complete(true);

            } else if (request.matches("PUT", PATH_PREFIX + "/retrieve", "/retrieve")) {

                responseWriter.writeResponse(request, retrieve(request), true);
                canHandle.complete(true);

            } else if (request.matches("PUT", PATH_PREFIX + "/verify", "/verify")) {

                Verification verification = getVerificationSerializer().deserialize(request.getBodyAsString());
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(VERIFICATION)
                        .setLogLevel(Level.INFO)
                        .setHttpRequest(verification.getHttpRequest())
                        .setMessageFormat("verifying requests that match:{}")
                        .setArguments(verification)
                );
                verify(verification, result -> {
                    if (isEmpty(result)) {
                        responseWriter.writeResponse(request, ACCEPTED);

                    } else {
                        responseWriter.writeResponse(request, NOT_ACCEPTABLE, result, MediaType.create("text", "plain").toString());
                    }
                    canHandle.complete(true);
                });

            } else if (request.matches("PUT", PATH_PREFIX + "/verifySequence", "/verifySequence")) {

                VerificationSequence verificationSequence = getVerificationSequenceSerializer().deserialize(request.getBodyAsString());
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(VERIFICATION)
                        .setLogLevel(Level.INFO)
                        .setHttpRequests(verificationSequence.getHttpRequests().toArray(new HttpRequest[0]))
                        .setMessageFormat("verifying sequence that match:{}")
                        .setArguments(verificationSequence)
                );
                verify(verificationSequence, result -> {
                    if (isEmpty(result)) {
                        responseWriter.writeResponse(request, ACCEPTED);
                    } else {
                        responseWriter.writeResponse(request, NOT_ACCEPTABLE, result, MediaType.create("text", "plain").toString());
                    }
                    canHandle.complete(true);
                });

            } else {
                canHandle.complete(false);
            }

            try {
                return canHandle.get(maxFutureTimeout(), MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException ignore) {
                return false;
            }

        } else {

            return false;

        }

    }

    @SuppressWarnings("rawtypes")
    private boolean validateSupportedFeatures(Expectation expectation, HttpRequest request, ResponseWriter responseWriter) {
        boolean valid = true;
        Action action = expectation.getAction();
        String NOT_SUPPORTED_MESSAGE = " is not supported by MockServer deployed as a WAR due to limitations in the JEE specification; use mockserver-netty to enable these features";
        if (action instanceof HttpResponse && ((HttpResponse) action).getConnectionOptions() != null) {
            valid = false;
            responseWriter.writeResponse(request, response("ConnectionOptions" + NOT_SUPPORTED_MESSAGE), true);
        } else if (action instanceof HttpObjectCallback) {
            valid = false;
            responseWriter.writeResponse(request, response("HttpObjectCallback" + NOT_SUPPORTED_MESSAGE), true);
        } else if (action instanceof HttpError) {
            valid = false;
            responseWriter.writeResponse(request, response("HttpError" + NOT_SUPPORTED_MESSAGE), true);
        }
        return valid;
    }

    public WebSocketClientRegistry getWebSocketClientRegistry() {
        return webSocketClientRegistry;
    }

    public RequestMatchers getRequestMatchers() {
        return requestMatchers;
    }

    public MockServerEventLog getMockServerLog() {
        return mockServerLog;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public String getUniqueLoopPreventionHeaderName() {
        return "x-forwarded-by";
    }

    public String getUniqueLoopPreventionHeaderValue() {
        return uniqueLoopPreventionHeaderValue;
    }

    public void stop() {
        if (expectationFileSystemPersistence != null) {
            expectationFileSystemPersistence.stop();
        }
        if (expectationFileWatcher != null) {
            expectationFileWatcher.stop();
        }
        getMockServerLog().stop();
    }

    private HttpRequestSerializer getHttpRequestSerializer() {
        if (this.httpRequestSerializer == null) {
            this.httpRequestSerializer = new HttpRequestSerializer(mockServerLogger);
        }
        return httpRequestSerializer;
    }

    private LogEventRequestAndResponseSerializer getHttpRequestResponseSerializer() {
        if (this.httpRequestResponseSerializer == null) {
            this.httpRequestResponseSerializer = new LogEventRequestAndResponseSerializer(mockServerLogger);
        }
        return httpRequestResponseSerializer;
    }

    private ExpectationSerializer getExpectationSerializer() {
        if (this.expectationSerializer == null) {
            this.expectationSerializer = new ExpectationSerializer(mockServerLogger);
        }
        return expectationSerializer;
    }

    private HttpRequestToJavaSerializer getHttpRequestToJavaSerializer() {
        if (this.httpRequestToJavaSerializer == null) {
            this.httpRequestToJavaSerializer = new HttpRequestToJavaSerializer();
        }
        return httpRequestToJavaSerializer;
    }

    private ExpectationToJavaSerializer getExpectationToJavaSerializer() {
        if (this.expectationToJavaSerializer == null) {
            this.expectationToJavaSerializer = new ExpectationToJavaSerializer();
        }
        return expectationToJavaSerializer;
    }

    private VerificationSerializer getVerificationSerializer() {
        if (this.verificationSerializer == null) {
            this.verificationSerializer = new VerificationSerializer(mockServerLogger);
        }
        return verificationSerializer;
    }

    private VerificationSequenceSerializer getVerificationSequenceSerializer() {
        if (this.verificationSequenceSerializer == null) {
            this.verificationSequenceSerializer = new VerificationSequenceSerializer(mockServerLogger);
        }
        return verificationSequenceSerializer;
    }

    private LogEntrySerializer getLogEntrySerializer() {
        if (this.logEntrySerializer == null) {
            this.logEntrySerializer = new LogEntrySerializer(mockServerLogger);
        }
        return logEntrySerializer;
    }

    private OpenAPIV3Parser getOpenAPIV3Parser() {
        if (this.openAPIV3Parser == null) {
            this.openAPIV3Parser = new OpenAPIV3Parser();
        }
        return openAPIV3Parser;
    }
}
