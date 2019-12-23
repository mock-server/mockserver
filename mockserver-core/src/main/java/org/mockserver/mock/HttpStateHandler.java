package org.mockserver.mock;

import org.apache.commons.lang3.StringUtils;
import org.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.persistence.ExpectationFileSystemPersistence;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.*;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.serialization.java.HttpRequestToJavaSerializer;
import org.mockserver.server.initialize.ExpectationInitializerLoader;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.socket.tls.KeyAndCertificateFactory.addSubjectAlternativeName;
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
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final ExpectationInitializerLoader expectationInitializerLoader;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final ExpectationFileSystemPersistence expectationFileSystemPersistence;
    // mockserver
    private MockServerMatcher mockServerMatcher;
    private final MockServerLogger mockServerLogger;
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

    public HttpStateHandler(MockServerLogger mockServerLogger, Scheduler scheduler) {
        this.mockServerLogger = mockServerLogger.setHttpStateHandler(this);
        this.scheduler = scheduler;
        this.webSocketClientRegistry = new WebSocketClientRegistry(mockServerLogger);
        this.mockServerLog = new MockServerEventLog(mockServerLogger, scheduler, true);
        this.mockServerMatcher = new MockServerMatcher(mockServerLogger, scheduler, webSocketClientRegistry);
        this.httpRequestSerializer = new HttpRequestSerializer(mockServerLogger);
        this.httpRequestResponseSerializer = new LogEventRequestAndResponseSerializer(mockServerLogger);
        this.expectationSerializer = new ExpectationSerializer(mockServerLogger);
        this.httpRequestToJavaSerializer = new HttpRequestToJavaSerializer();
        this.expectationToJavaSerializer = new ExpectationToJavaSerializer();
        this.verificationSerializer = new VerificationSerializer(mockServerLogger);
        this.verificationSequenceSerializer = new VerificationSequenceSerializer(mockServerLogger);
        this.logEntrySerializer = new LogEntrySerializer(mockServerLogger);
        this.expectationInitializerLoader = new ExpectationInitializerLoader(mockServerLogger, mockServerMatcher);
        this.expectationFileSystemPersistence = new ExpectationFileSystemPersistence(mockServerLogger, mockServerMatcher);
    }

    public MockServerLogger getMockServerLogger() {
        return mockServerLogger;
    }

    public void clear(HttpRequest request) {
        HttpRequest requestMatcher = null;
        if (isNotBlank(request.getBodyAsString())) {
            requestMatcher = httpRequestSerializer.deserialize(request.getBodyAsString());
        }
        try {
            ClearType retrieveType = ClearType.valueOf(StringUtils.defaultIfEmpty(request.getFirstQueryStringParameter("type").toUpperCase(), "ALL"));
            switch (retrieveType) {
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
                    mockServerMatcher.clear(requestMatcher);
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
                    mockServerMatcher.clear(requestMatcher);
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
    }

    public void reset() {
        mockServerMatcher.reset();
        mockServerLog.reset();
        webSocketClientRegistry.reset();
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(CLEARED)
                .setLogLevel(Level.INFO)
                .setHttpRequest(request())
                .setMessageFormat("resetting all expectations and request logs")
        );
    }

    public void add(Expectation... expectations) {
        for (Expectation expectation : expectations) {
            if (expectation.getHttpRequest() != null) {
                final String hostHeader = expectation.getHttpRequest().getFirstHeader(HOST.toString());
                if (isNotBlank(hostHeader)) {
                    scheduler.submit(() -> addSubjectAlternativeName(hostHeader));
                }
            }
            mockServerMatcher.add(expectation);
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.INFO)
                    .setType(CREATED_EXPECTATION)
                    .setHttpRequest(expectation.getHttpRequest())
                    .setMessageFormat("creating expectation:{}")
                    .setArguments(expectation.clone())
            );
        }
    }

    public Expectation firstMatchingExpectation(HttpRequest request) {
        if (mockServerMatcher.isEmpty()) {
            return null;
        } else {
            return mockServerMatcher.firstMatchingExpectation(request);
        }
    }

    public void postProcess(Expectation expectation) {
        mockServerMatcher.postProcess(expectation);
    }

    public void log(LogEntry logEntry) {
        if (mockServerLog != null) {
            mockServerLog.add(logEntry);
        }
    }

    public HttpResponse retrieve(HttpRequest request) {
        CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();
        HttpResponse response = response().withStatusCode(200);
        if (request != null) {
            try {
                final HttpRequest httpRequest = isNotBlank(request.getBodyAsString()) ? httpRequestSerializer.deserialize(request.getBodyAsString()) : null;
                Format format = Format.valueOf(StringUtils.defaultIfEmpty(request.getFirstQueryStringParameter("format").toUpperCase(), "JSON"));
                RetrieveType retrieveType = RetrieveType.valueOf(StringUtils.defaultIfEmpty(request.getFirstQueryStringParameter("type").toUpperCase(), "REQUESTS"));
                switch (retrieveType) {
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
                                                httpRequestToJavaSerializer.serialize(requests),
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
                                                httpRequestSerializer.serialize(requests),
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
                                                logEntrySerializer.serialize(logEntries),
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
                                                httpRequestResponseSerializer.serialize(httpRequestAndHttpResponses),
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
                                                logEntrySerializer.serialize(logEntries),
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
                                                expectationToJavaSerializer.serialize(requests),
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
                                                expectationSerializer.serialize(requests),
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
                                                logEntrySerializer.serialize(logEntries),
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
                        final Object[] arguments = new Object[]{(httpRequest == null ? request() : httpRequest)};
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setType(RETRIEVED)
                                .setLogLevel(Level.INFO)
                                .setHttpRequest(httpRequest)
                                .setMessageFormat("retrieving active expectations in " + format.name().toLowerCase() + " that match:{}")
                                .setArguments(arguments)
                        );
                        List<Expectation> expectations = mockServerMatcher.retrieveActiveExpectations(httpRequest);
                        switch (format) {
                            case JAVA:
                                response.withBody(expectationToJavaSerializer.serialize(expectations), MediaType.create("application", "java").withCharset(UTF_8));
                                break;
                            case JSON:
                                response.withBody(expectationSerializer.serialize(expectations), MediaType.JSON_UTF_8);
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
                    return httpResponseFuture.get();
                } catch (ExecutionException | InterruptedException e) {
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
        mockServerLog.verify(verification, result::complete);
        return result;
    }

    public void verify(Verification verification, Consumer<String> resultConsumer) {
        mockServerLog.verify(verification, resultConsumer);
    }

    public Future<String> verify(VerificationSequence verification) {
        CompletableFuture<String> result = new CompletableFuture<>();
        mockServerLog.verify(verification, result::complete);
        return result;
    }

    public void verify(VerificationSequence verification, Consumer<String> resultConsumer) {
        mockServerLog.verify(verification, resultConsumer);
    }

    public boolean handle(HttpRequest request, ResponseWriter responseWriter, boolean warDeployment) {
        CompletableFuture<Boolean> canHandle = new CompletableFuture<>();

        mockServerLogger.logEvent(
            new LogEntry()
                .setType(LogEntry.LogMessageType.TRACE)
                .setLogLevel(TRACE)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}")
                .setArguments(request)
        );

        if (request.matches("PUT", PATH_PREFIX + "/expectation", "/expectation")) {

            for (Expectation expectation : expectationSerializer.deserializeArray(request.getBodyAsString())) {
                if (!warDeployment || validateSupportedFeatures(expectation, request, responseWriter)) {
                    add(expectation);
                }
            }
            responseWriter.writeResponse(request, CREATED);
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

            Verification verification = verificationSerializer.deserialize(request.getBodyAsString());
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(VERIFICATION)
                    .setLogLevel(Level.INFO)
                    .setHttpRequest(verification.getHttpRequest())
                    .setMessageFormat("verifying requests that match:{}")
                    .setArguments(verification)
            );
            verify(verification, result -> {
                if (StringUtils.isEmpty(result)) {
                    responseWriter.writeResponse(request, ACCEPTED);

                } else {
                    responseWriter.writeResponse(request, NOT_ACCEPTABLE, result, MediaType.create("text", "plain").toString());
                }
                canHandle.complete(true);
            });

        } else if (request.matches("PUT", PATH_PREFIX + "/verifySequence", "/verifySequence")) {

            VerificationSequence verificationSequence = verificationSequenceSerializer.deserialize(request.getBodyAsString());
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(VERIFICATION)
                    .setLogLevel(Level.INFO)
                    .setHttpRequests(verificationSequence.getHttpRequests().toArray(new HttpRequest[0]))
                    .setMessageFormat("verifying sequence that match:{}")
                    .setArguments(verificationSequence)
            );
            verify(verificationSequence, result -> {
                if (StringUtils.isEmpty(result)) {
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
            return canHandle.get();
        } catch (InterruptedException | ExecutionException ignore) {
            return false;
        }
    }

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

    public MockServerMatcher getMockServerMatcher() {
        return mockServerMatcher;
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
}
