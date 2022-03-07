package org.mockserver.mock;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.authentication.AuthenticationException;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.closurecallback.websocketregistry.LocalCallbackRegistry;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.memory.MemoryMonitoring;
import org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause;
import org.mockserver.model.*;
import org.mockserver.openapi.OpenAPIConverter;
import org.mockserver.persistence.ExpectationFileSystemPersistence;
import org.mockserver.persistence.ExpectationFileWatcher;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.*;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.server.initialize.ExpectationInitializerLoader;
import org.mockserver.uuid.UUIDService;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
import static org.mockserver.log.model.LogEntry.LogMessageType.CLEARED;
import static org.mockserver.log.model.LogEntry.LogMessageType.RETRIEVED;
import static org.mockserver.log.model.LogEntryMessages.RECEIVED_REQUEST_MESSAGE_FORMAT;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.openapi.OpenAPIParser.OPEN_API_LOAD_ERROR;

/**
 * @author jamesdbloom
 */
public class HttpState {

    public static final String LOG_SEPARATOR = NEW_LINE + "------------------------------------" + NEW_LINE;
    public static final String PATH_PREFIX = "/mockserver";
    private static final ThreadLocal<Integer> LOCAL_PORT = new ThreadLocal<>();
    private final String uniqueLoopPreventionHeaderValue = "MockServer_" + UUIDService.getUUID();
    private final MockServerEventLog mockServerLog;
    private final Scheduler scheduler;
    private ExpectationFileSystemPersistence expectationFileSystemPersistence;
    private ExpectationFileWatcher expectationFileWatcher;
    // mockserver
    private final RequestMatchers requestMatchers;
    private final Configuration configuration;
    private final MockServerLogger mockServerLogger;
    private final WebSocketClientRegistry webSocketClientRegistry;
    // serializers
    private ExpectationIdSerializer expectationIdSerializer;
    private RequestDefinitionSerializer requestDefinitionSerializer;
    private LogEventRequestAndResponseSerializer httpRequestResponseSerializer;
    private ExpectationSerializer expectationSerializer;
    private OpenAPIExpectationSerializer openAPIExpectationSerializer;
    private ExpectationToJavaSerializer expectationToJavaSerializer;
    private VerificationSerializer verificationSerializer;
    private VerificationSequenceSerializer verificationSequenceSerializer;
    private LogEntrySerializer logEntrySerializer;
    private final MemoryMonitoring memoryMonitoring;
    private OpenAPIConverter openAPIConverter;
    private AuthenticationHandler controlPlaneAuthenticationHandler;

    public static void setPort(final HttpRequest request) {
        if (request != null && request.getSocketAddress() != null) {
            setPort(request.getSocketAddress().getPort());
            request.withSocketAddress(null);
        }
    }

    public static void setPort(final Integer port) {
        LOCAL_PORT.set(port);
    }

    public static void setPort(final Integer... port) {
        if (port != null && port.length > 0) {
            setPort(port[0]);
        }
    }

    public static void setPort(final List<Integer> port) {
        if (port != null && port.size() > 0) {
            setPort(port.get(0));
        }
    }

    public static Integer getPort() {
        return LOCAL_PORT.get();
    }

    public HttpState(Configuration configuration, MockServerLogger mockServerLogger, Scheduler scheduler) {
        this.configuration = configuration;
        this.mockServerLogger = mockServerLogger.setHttpStateHandler(this);
        this.scheduler = scheduler;
        this.webSocketClientRegistry = new WebSocketClientRegistry(configuration, mockServerLogger);
        LocalCallbackRegistry.setMaxWebSocketExpectations(configuration.maxWebSocketExpectations());
        this.mockServerLog = new MockServerEventLog(configuration, mockServerLogger, scheduler, true);
        this.requestMatchers = new RequestMatchers(configuration, mockServerLogger, scheduler, webSocketClientRegistry);
        if (configuration.persistExpectations()) {
            this.expectationFileSystemPersistence = new ExpectationFileSystemPersistence(configuration, mockServerLogger, requestMatchers);
        }
        if (configuration.watchInitializationJson()) {
            this.expectationFileWatcher = new ExpectationFileWatcher(configuration, mockServerLogger, requestMatchers);
        }
        this.memoryMonitoring = new MemoryMonitoring(configuration, this.mockServerLog, this.requestMatchers);
        new ExpectationInitializerLoader(configuration, mockServerLogger, requestMatchers);
    }

    public void setControlPlaneAuthenticationHandler(AuthenticationHandler controlPlaneAuthenticationHandler) {
        this.controlPlaneAuthenticationHandler = controlPlaneAuthenticationHandler;
    }

    public MockServerLogger getMockServerLogger() {
        return mockServerLogger;
    }

    public void clear(HttpRequest request) {
        final String logCorrelationId = UUIDService.getUUID();
        RequestDefinition requestDefinition = null;
        ExpectationId expectationId = null;
        if (isNotBlank(request.getBodyAsString())) {
            String body = request.getBodyAsJsonOrXmlString();
            try {
                expectationId = getExpectationIdSerializer().deserialize(body);
            } catch (Throwable throwable) {
                // assume not expectationId
                requestDefinition = getRequestDefinitionSerializer().deserialize(body);
            }
            if (expectationId != null) {
                requestDefinition = resolveExpectationId(expectationId);
            }
        }
        if (requestDefinition != null) {
            requestDefinition.withLogCorrelationId(logCorrelationId);
        }
        try {
            ClearType type = ClearType.valueOf(defaultIfEmpty(request.getFirstQueryStringParameter("type").toUpperCase(), "ALL"));
            switch (type) {
                case LOG:
                    mockServerLog.clear(requestDefinition);
                    break;
                case EXPECTATIONS:
                    if (expectationId != null) {
                        requestMatchers.clear(expectationId, logCorrelationId);
                    } else {
                        requestMatchers.clear(requestDefinition);
                    }
                    break;
                case ALL:
                    mockServerLog.clear(requestDefinition);
                    if (expectationId != null) {
                        requestMatchers.clear(expectationId, logCorrelationId);
                    } else {
                        requestMatchers.clear(requestDefinition);
                    }
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
    }

    private RequestDefinition resolveExpectationId(ExpectationId expectationId) {
        return requestMatchers
            .retrieveRequestDefinitions(Collections.singletonList(expectationId))
            .findFirst()
            .orElse(null);
    }

    private List<RequestDefinition> resolveExpectationIds(List<ExpectationId> expectationIds) {
        return requestMatchers
            .retrieveRequestDefinitions(expectationIds)
            .collect(Collectors.toList());
    }

    public void reset() {
        requestMatchers.reset();
        mockServerLog.reset();
        webSocketClientRegistry.reset();
        if (MockServerLogger.isEnabled(Level.INFO)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(CLEARED)
                    .setLogLevel(Level.INFO)
                    .setHttpRequest(request())
                    .setMessageFormat("resetting all expectations and request logs")
            );
        }
        new Scheduler.SchedulerThreadFactory("MockServer Memory Metrics").newThread(() -> {
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
                    scheduler.submit(() -> configuration.addSubjectAlternativeName(hostHeader));
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

    @VisibleForTesting
    public List<Expectation> allMatchingExpectation(HttpRequest request) {
        if (requestMatchers.isEmpty()) {
            return Collections.emptyList();
        } else {
            return requestMatchers.retrieveActiveExpectations(request);
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
        final String logCorrelationId = UUIDService.getUUID();
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
                            if (MockServerLogger.isEnabled(Level.INFO)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setType(RETRIEVED)
                                        .setLogLevel(Level.INFO)
                                        .setCorrelationId(logCorrelationId)
                                        .setHttpRequest(requestDefinition)
                                        .setMessageFormat("retrieved logs that match:{}")
                                        .setArguments(requestDefinition)
                                );
                            }
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
                        if (MockServerLogger.isEnabled(Level.INFO)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setType(RETRIEVED)
                                    .setLogLevel(Level.INFO)
                                    .setCorrelationId(logCorrelationId)
                                    .setHttpRequest(requestDefinition)
                                    .setMessageFormat("retrieved " + expectations.size() + " active expectations in " + format.name().toLowerCase() + " that match:{}")
                                    .setArguments(requestDefinition)
                            );
                        }
                        httpResponseFuture.complete(response);
                        break;
                    }
                }

                try {
                    return httpResponseFuture.get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
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
                }
                if (iae.getMessage().contains(Format.class.getSimpleName())) {
                    throw new IllegalArgumentException("\"" + request.getFirstQueryStringParameter("format") + "\" is not a valid value for \"format\" parameter, only the following values are supported " + Arrays.stream(Format.values()).map(input -> input.name().toLowerCase()).collect(Collectors.toList()));
                }
                throw iae;
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
        if (verification.getExpectationId() != null) {
            verification.withRequest(resolveExpectationId(verification.getExpectationId()));
        }
        mockServerLog.verify(verification, resultConsumer);
    }

    public Future<String> verify(VerificationSequence verification) {
        CompletableFuture<String> result = new CompletableFuture<>();
        verify(verification, result::complete);
        return result;
    }

    public void verify(VerificationSequence verificationSequence, Consumer<String> resultConsumer) {
        if (verificationSequence.getExpectationIds() != null && !verificationSequence.getExpectationIds().isEmpty()) {
            verificationSequence.withRequests(resolveExpectationIds(verificationSequence.getExpectationIds()));
        }
        mockServerLog.verify(verificationSequence, resultConsumer);
    }

    public boolean handle(HttpRequest request, ResponseWriter responseWriter, boolean warDeployment) {

        request.withLogCorrelationId(UUIDService.getUUID());
        setPort(request);

        if (MockServerLogger.isEnabled(Level.TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.TRACE)
                    .setHttpRequest(request)
                    .setMessageFormat(RECEIVED_REQUEST_MESSAGE_FORMAT)
                    .setArguments(request)
            );
        }

        if (request.matches("PUT")) {

            CompletableFuture<Boolean> canHandle = new CompletableFuture<>();

            if (request.matches("PUT", PATH_PREFIX + "/expectation", "/expectation")) {

                if (controlPlaneRequestAuthenticated(request, responseWriter)) {
                    List<Expectation> upsertedExpectations = new ArrayList<>();
                    for (Expectation expectation : getExpectationSerializer().deserializeArray(request.getBodyAsJsonOrXmlString(), false)) {
                        if (!warDeployment || validateSupportedFeatures(expectation, request, responseWriter)) {
                            upsertedExpectations.addAll(add(expectation));
                        }
                    }

                    responseWriter.writeResponse(request, response()
                        .withStatusCode(CREATED.code())
                        .withBody(getExpectationSerializer().serialize(upsertedExpectations), MediaType.JSON_UTF_8), true);
                }
                canHandle.complete(true);

            } else if (request.matches("PUT", PATH_PREFIX + "/openapi", "/openapi")) {

                if (controlPlaneRequestAuthenticated(request, responseWriter)) {
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
                }
                canHandle.complete(true);

            } else if (request.matches("PUT", PATH_PREFIX + "/clear", "/clear")) {

                if (controlPlaneRequestAuthenticated(request, responseWriter)) {
                    clear(request);
                    responseWriter.writeResponse(request, OK);
                }
                canHandle.complete(true);

            } else if (request.matches("PUT", PATH_PREFIX + "/reset", "/reset")) {

                if (controlPlaneRequestAuthenticated(request, responseWriter)) {
                    reset();
                    responseWriter.writeResponse(request, OK);
                }
                canHandle.complete(true);

            } else if (request.matches("PUT", PATH_PREFIX + "/retrieve", "/retrieve")) {

                if (controlPlaneRequestAuthenticated(request, responseWriter)) {
                    responseWriter.writeResponse(request, retrieve(request), true);
                }
                canHandle.complete(true);

            } else if (request.matches("PUT", PATH_PREFIX + "/verify", "/verify")) {

                if (controlPlaneRequestAuthenticated(request, responseWriter)) {
                    verify(getVerificationSerializer().deserialize(request.getBodyAsJsonOrXmlString()), result -> {
                        if (isEmpty(result)) {
                            responseWriter.writeResponse(request, ACCEPTED);
                        } else {
                            responseWriter.writeResponse(request, NOT_ACCEPTABLE, result, MediaType.create("text", "plain").toString());
                        }
                        canHandle.complete(true);
                    });
                } else {
                    canHandle.complete(true);
                }

            } else if (request.matches("PUT", PATH_PREFIX + "/verifySequence", "/verifySequence")) {

                if (controlPlaneRequestAuthenticated(request, responseWriter)) {
                    verify(getVerificationSequenceSerializer().deserialize(request.getBodyAsJsonOrXmlString()), result -> {
                        if (isEmpty(result)) {
                            responseWriter.writeResponse(request, ACCEPTED);
                        } else {
                            responseWriter.writeResponse(request, NOT_ACCEPTABLE, result, MediaType.create("text", "plain").toString());
                        }
                        canHandle.complete(true);
                    });
                } else {
                    canHandle.complete(true);
                }

            } else {

                canHandle.complete(false);

            }

            try {
                return canHandle.get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
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

    private boolean controlPlaneRequestAuthenticated(HttpRequest request, ResponseWriter responseWriter) {
        try {
            if (controlPlaneAuthenticationHandler == null || controlPlaneAuthenticationHandler.controlPlaneRequestAuthenticated(request)) {
                return true;
            }
        } catch (AuthenticationException authenticationException) {
            responseWriter.writeResponse(request, UNAUTHORIZED, "Unauthorized for control plane - " + authenticationException.getMessage(), MediaType.create("text", "plain").toString());
            return false;
        }
        responseWriter.writeResponse(request, UNAUTHORIZED, "Unauthorized for control plane", MediaType.create("text", "plain").toString());
        return false;
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

    private ExpectationIdSerializer getExpectationIdSerializer() {
        if (this.expectationIdSerializer == null) {
            this.expectationIdSerializer = new ExpectationIdSerializer(mockServerLogger);
        }
        return expectationIdSerializer;
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
