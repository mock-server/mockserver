package org.mockserver.mock;

import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.memory.MemoryMonitoring;
import org.mockserver.model.*;
import org.mockserver.openapi.OpenAPIConverter;
import org.mockserver.persistence.ExpectationFileSystemPersistence;
import org.mockserver.persistence.ExpectationFileWatcher;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.*;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;
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
import static org.mockserver.log.model.LogEntry.LogMessageType.CLEARED;
import static org.mockserver.log.model.LogEntry.LogMessageType.RETRIEVED;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.openapi.OpenAPISerialiser.OPEN_API_LOAD_ERROR;

/**
 * @author jamesdbloom
 */
public class HttpState {

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
    private RequestDefinitionSerializer requestDefinitionSerializer;
    private LogEventRequestAndResponseSerializer httpRequestResponseSerializer;
    private ExpectationSerializer expectationSerializer;
    private OpenAPIExpectationSerializer openAPIExpectationSerializer;
    private ExpectationToJavaSerializer expectationToJavaSerializer;
    private VerificationSerializer verificationSerializer;
    private VerificationSequenceSerializer verificationSequenceSerializer;
    private LogEntrySerializer logEntrySerializer;
    private MemoryMonitoring memoryMonitoring;
    private OpenAPIConverter openAPIConverter;

    public HttpState(MockServerLogger mockServerLogger, Scheduler scheduler) {
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
        final String logCorrelationId = UUID.randomUUID().toString();
        RequestDefinition requestDefinition = null;
        if (isNotBlank(request.getBodyAsString())) {
            requestDefinition = getRequestDefinitionSerializer().deserialize(request.getBodyAsJsonOrXmlString());
            requestDefinition.withLogCorrelationId(logCorrelationId);
        }
        try {
            ClearType type = ClearType.valueOf(defaultIfEmpty(request.getFirstQueryStringParameter("type").toUpperCase(), "ALL"));
            switch (type) {
                case LOG:
                    mockServerLog.clear(requestDefinition);
                    break;
                case EXPECTATIONS:
                    requestMatchers.clear(requestDefinition);
                    break;
                case ALL:
                    mockServerLog.clear(requestDefinition);
                    requestMatchers.clear(requestDefinition);
                    break;
            }
        } catch (IllegalArgumentException iae) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setCorrelationId(logCorrelationId)
                    .setMessageFormat("exception handling request:{}error:{}")
                    .setArguments(request, iae.getMessage())
                    .setThrowable(iae)
            );
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
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception handling reset request:{}")
                        .setArguments(ie.getMessage())
                        .setThrowable(ie)
                );
                ie.printStackTrace();
            }
        });
    }

    public List<Expectation> add(OpenAPIExpectation openAPIExpectation) {
        return getOpenAPIConverter().buildExpectations(openAPIExpectation.getSpecUrlOrPayload(), openAPIExpectation.getOperationsAndResponses()).stream().map(this::add).flatMap(List::stream).collect(Collectors.toList());
    }

    public List<Expectation> add(Expectation... expectations) {
        List<Expectation> upsertedExpectations = new ArrayList<>();
        for (Expectation expectation : expectations) {
            RequestDefinition requestDefinition = expectation.getHttpRequest();
            if (requestDefinition instanceof HttpRequest) {
                final String hostHeader = ((HttpRequest) requestDefinition).getFirstHeader(HOST.toString());
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
        final String logCorrelationId = UUID.randomUUID().toString();
        CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();
        HttpResponse response = response().withStatusCode(OK.code());
        if (request != null) {
            try {
                final RequestDefinition requestDefinition = isNotBlank(request.getBodyAsString()) ? getRequestDefinitionSerializer().deserialize(request.getBodyAsJsonOrXmlString()) : request();
                requestDefinition.withLogCorrelationId(logCorrelationId);
                Format format = Format.valueOf(defaultIfEmpty(request.getFirstQueryStringParameter("format").toUpperCase(), "JSON"));
                RetrieveType type = RetrieveType.valueOf(defaultIfEmpty(request.getFirstQueryStringParameter("type").toUpperCase(), "REQUESTS"));
                switch (type) {
                    case LOGS: {
                        mockServerLog.retrieveMessageLogEntries(requestDefinition, (List<LogEntry> logEntries) -> {
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
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setType(RETRIEVED)
                                    .setLogLevel(Level.INFO)
                                    .setCorrelationId(logCorrelationId)
                                    .setHttpRequest(requestDefinition)
                                    .setMessageFormat("retrieved logs that match:{}")
                                    .setArguments(requestDefinition)
                            );
                            httpResponseFuture.complete(response);
                        });
                        break;
                    }
                    case REQUESTS: {
                        LogEntry logEntry = new LogEntry()
                            .setType(RETRIEVED)
                            .setLogLevel(Level.INFO)
                            .setCorrelationId(logCorrelationId)
                            .setHttpRequest(requestDefinition)
                            .setMessageFormat("retrieved requests in " + format.name().toLowerCase() + " that match:{}")
                            .setArguments(requestDefinition);
                        switch (format) {
                            case JAVA:
                                mockServerLog
                                    .retrieveRequests(
                                        requestDefinition,
                                        requests -> {
                                            response.withBody(
                                                getRequestDefinitionSerializer().serialize(requests),
                                                MediaType.create("application", "java").withCharset(UTF_8)
                                            );
                                            mockServerLogger.logEvent(logEntry);
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                            case JSON:
                                mockServerLog
                                    .retrieveRequests(
                                        requestDefinition,
                                        requests -> {
                                            response.withBody(
                                                getRequestDefinitionSerializer().serialize(true, requests),
                                                MediaType.JSON_UTF_8
                                            );
                                            mockServerLogger.logEvent(logEntry);
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                            case LOG_ENTRIES:
                                mockServerLog
                                    .retrieveRequestLogEntries(
                                        requestDefinition,
                                        logEntries -> {
                                            response.withBody(
                                                getLogEntrySerializer().serialize(logEntries),
                                                MediaType.JSON_UTF_8
                                            );
                                            mockServerLogger.logEvent(logEntry);
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                        }
                        break;
                    }
                    case REQUEST_RESPONSES: {
                        LogEntry logEntry = new LogEntry()
                            .setType(RETRIEVED)
                            .setLogLevel(Level.INFO)
                            .setCorrelationId(logCorrelationId)
                            .setHttpRequest(requestDefinition)
                            .setMessageFormat("retrieved requests and responses in " + format.name().toLowerCase() + " that match:{}")
                            .setArguments(requestDefinition);
                        switch (format) {
                            case JAVA:
                                response.withBody("JAVA not supported for REQUEST_RESPONSES", MediaType.create("text", "plain").withCharset(UTF_8));
                                mockServerLogger.logEvent(logEntry);
                                httpResponseFuture.complete(response);
                                break;
                            case JSON:
                                mockServerLog
                                    .retrieveRequestResponses(
                                        requestDefinition,
                                        httpRequestAndHttpResponses -> {
                                            response.withBody(
                                                getHttpRequestResponseSerializer().serialize(httpRequestAndHttpResponses),
                                                MediaType.JSON_UTF_8
                                            );
                                            mockServerLogger.logEvent(logEntry);
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                            case LOG_ENTRIES:
                                mockServerLog
                                    .retrieveRequestResponseMessageLogEntries(
                                        requestDefinition,
                                        logEntries -> {
                                            response.withBody(
                                                getLogEntrySerializer().serialize(logEntries),
                                                MediaType.JSON_UTF_8
                                            );
                                            mockServerLogger.logEvent(logEntry);
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                        }
                        break;
                    }
                    case RECORDED_EXPECTATIONS: {
                        LogEntry logEntry = new LogEntry()
                            .setType(RETRIEVED)
                            .setLogLevel(Level.INFO)
                            .setCorrelationId(logCorrelationId)
                            .setHttpRequest(requestDefinition)
                            .setMessageFormat("retrieved recorded expectations in " + format.name().toLowerCase() + " that match:{}")
                            .setArguments(requestDefinition);
                        switch (format) {
                            case JAVA:
                                mockServerLog
                                    .retrieveRecordedExpectations(
                                        requestDefinition,
                                        requests -> {
                                            response.withBody(
                                                getExpectationToJavaSerializer().serialize(requests),
                                                MediaType.create("application", "java").withCharset(UTF_8)
                                            );
                                            mockServerLogger.logEvent(logEntry);
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                            case JSON:
                                mockServerLog
                                    .retrieveRecordedExpectations(
                                        requestDefinition,
                                        requests -> {
                                            response.withBody(
                                                getExpectationSerializer().serialize(requests),
                                                MediaType.JSON_UTF_8
                                            );
                                            mockServerLogger.logEvent(logEntry);
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                            case LOG_ENTRIES:
                                mockServerLog
                                    .retrieveRecordedExpectationLogEntries(
                                        requestDefinition,
                                        logEntries -> {
                                            response.withBody(
                                                getLogEntrySerializer().serialize(logEntries),
                                                MediaType.JSON_UTF_8
                                            );
                                            mockServerLogger.logEvent(logEntry);
                                            httpResponseFuture.complete(response);
                                        }
                                    );
                                break;
                        }
                        break;
                    }
                    case ACTIVE_EXPECTATIONS: {
                        List<Expectation> expectations = requestMatchers.retrieveActiveExpectations(requestDefinition);
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
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(RETRIEVED)
                                .setLogLevel(Level.INFO)
                                .setCorrelationId(logCorrelationId)
                                .setHttpRequest(requestDefinition)
                                .setMessageFormat("retrieved active expectations in " + format.name().toLowerCase() + " that match:{}")
                                .setArguments(requestDefinition)
                        );
                        httpResponseFuture.complete(response);
                        break;
                    }
                }

                try {
                    return httpResponseFuture.get(maxFutureTimeout(), MILLISECONDS);
                } catch (ExecutionException | InterruptedException | TimeoutException ex) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setCorrelationId(logCorrelationId)
                            .setMessageFormat("exception handling request:{}error:{}")
                            .setArguments(request, ex.getMessage())
                            .setThrowable(ex)
                    );
                    throw new RuntimeException("Exception retrieving state for " + request, ex);
                }
            } catch (IllegalArgumentException iae) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setCorrelationId(logCorrelationId)
                        .setMessageFormat("exception handling request:{}error:{}")
                        .setArguments(request, iae.getMessage())
                        .setThrowable(iae)
                );
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

        request.withLogCorrelationId(UUID.randomUUID().toString());

        if (MockServerLogger.isEnabled(Level.TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.TRACE)
                    .setHttpRequest(request)
                    .setMessageFormat("received request:{}")
                    .setArguments(request)
            );
        }

        if (request.matches("PUT")) {

            CompletableFuture<Boolean> canHandle = new CompletableFuture<>();

            if (request.matches("PUT", PATH_PREFIX + "/expectation", "/expectation")) {

                List<Expectation> upsertedExpectations = new ArrayList<>();
                for (Expectation expectation : getExpectationSerializer().deserializeArray(request.getBodyAsJsonOrXmlString(), false)) {
                    if (!warDeployment || validateSupportedFeatures(expectation, request, responseWriter)) {
                        upsertedExpectations.addAll(add(expectation));
                    }
                }

                responseWriter.writeResponse(request, response()
                    .withStatusCode(CREATED.code())
                    .withBody(getExpectationSerializer().serialize(upsertedExpectations), MediaType.JSON_UTF_8), true);
                canHandle.complete(true);

            } else if (request.matches("PUT", PATH_PREFIX + "/openapi", "/openapi")) {

                try {
                    List<Expectation> upsertedExpectations = new ArrayList<>();
                    for (OpenAPIExpectation openAPIExpectation : getOpenAPIExpectationSerializer().deserializeArray(request.getBodyAsJsonOrXmlString(), false)) {
                        upsertedExpectations.addAll(add(openAPIExpectation));
                    }
                    responseWriter.writeResponse(request, response()
                        .withStatusCode(CREATED.code())
                        .withBody(getExpectationSerializer().serialize(upsertedExpectations), MediaType.JSON_UTF_8), true);
                } catch (IllegalArgumentException iae) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception handling request for open api expectation:{}error:{}")
                            .setArguments(request, iae.getMessage())
                            .setThrowable(iae)
                    );
                    responseWriter.writeResponse(
                        request,
                        BAD_REQUEST,
                        (!iae.getMessage().startsWith(OPEN_API_LOAD_ERROR) ? OPEN_API_LOAD_ERROR + (isNotBlank(iae.getMessage()) ? ", " : "") : "") + iae.getMessage(),
                        MediaType.create("text", "plain").toString()
                    );
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

                verify(getVerificationSerializer().deserialize(request.getBodyAsJsonOrXmlString()), result -> {
                    if (isEmpty(result)) {
                        responseWriter.writeResponse(request, ACCEPTED);
                    } else {
                        responseWriter.writeResponse(request, NOT_ACCEPTABLE, result, MediaType.create("text", "plain").toString());
                    }
                    canHandle.complete(true);
                });

            } else if (request.matches("PUT", PATH_PREFIX + "/verifySequence", "/verifySequence")) {

                verify(getVerificationSequenceSerializer().deserialize(request.getBodyAsJsonOrXmlString()), result -> {
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
            } catch (InterruptedException | ExecutionException | TimeoutException ex) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception handling request:{}error:{}")
                        .setArguments(request, ex.getMessage())
                        .setThrowable(ex)
                );
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

    private RequestDefinitionSerializer getRequestDefinitionSerializer() {
        if (this.requestDefinitionSerializer == null) {
            this.requestDefinitionSerializer = new RequestDefinitionSerializer(mockServerLogger);
        }
        return requestDefinitionSerializer;
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

    private OpenAPIExpectationSerializer getOpenAPIExpectationSerializer() {
        if (this.openAPIExpectationSerializer == null) {
            this.openAPIExpectationSerializer = new OpenAPIExpectationSerializer(mockServerLogger);
        }
        return openAPIExpectationSerializer;
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

    private OpenAPIConverter getOpenAPIConverter() {
        if (this.openAPIConverter == null) {
            this.openAPIConverter = new OpenAPIConverter(mockServerLogger);
        }
        return openAPIConverter;
    }
}
