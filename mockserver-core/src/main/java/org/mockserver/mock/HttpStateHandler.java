package org.mockserver.mock;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.filters.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.log.model.MessageLogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.*;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.serialization.java.HttpRequestToJavaSerializer;
import org.mockserver.server.initialize.ExpectationInitializerLoader;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static com.google.common.net.MediaType.*;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.MessageLogEntry.LogMessageType.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.socket.tls.KeyAndCertificateFactory.addSubjectAlternativeName;

/**
 * @author jamesdbloom
 */
public class HttpStateHandler {

    public static final String LOG_SEPARATOR = NEW_LINE + "------------------------------------" + NEW_LINE;
    public static final String PATH_PREFIX = "/mockserver";
    private final MockServerEventLog mockServerLog;
    private final Scheduler scheduler;
    // mockserver
    private MockServerMatcher mockServerMatcher;
    private MockServerLogger mockServerLogger = new MockServerLogger(LoggerFactory.getLogger(this.getClass()), this);
    private WebSocketClientRegistry webSocketClientRegistry = new WebSocketClientRegistry();
    // serializers
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer(mockServerLogger);
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer(mockServerLogger);
    private HttpRequestToJavaSerializer httpRequestToJavaSerializer = new HttpRequestToJavaSerializer();
    private ExpectationToJavaSerializer expectationToJavaSerializer = new ExpectationToJavaSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer(mockServerLogger);
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer(mockServerLogger);
    private LogEntrySerializer logEntrySerializer = new LogEntrySerializer(mockServerLogger);

    public HttpStateHandler(Scheduler scheduler) {
        this.scheduler = scheduler;
        mockServerLog = new MockServerEventLog(mockServerLogger, scheduler);
        mockServerMatcher = new MockServerMatcher(mockServerLogger, scheduler);
        addExpectationsFromInitializer();
    }

    private void addExpectationsFromInitializer() {
        for (Expectation expectation : ExpectationInitializerLoader.loadExpectations()) {
            mockServerMatcher.add(expectation);
        }
    }

    public MockServerLogger getMockServerLogger() {
        return mockServerLogger;
    }

    public void clear(HttpRequest request) {
        HttpRequest requestMatcher = null;
        if (!Strings.isNullOrEmpty(request.getBodyAsString())) {
            requestMatcher = httpRequestSerializer.deserialize(request.getBodyAsString());
        }
        try {
            ClearType retrieveType = ClearType.valueOf(StringUtils.defaultIfEmpty(request.getFirstQueryStringParameter("type").toUpperCase(), "ALL"));
            switch (retrieveType) {
                case LOG:
                    mockServerLog.clear(requestMatcher);
                    mockServerLogger.info(CLEARED, requestMatcher, "clearing recorded requests and logs that match:{}", (requestMatcher == null ? "{}" : requestMatcher));
                    break;
                case EXPECTATIONS:
                    mockServerMatcher.clear(requestMatcher);
                    mockServerLogger.info(CLEARED, requestMatcher, "clearing expectations that match:{}", (requestMatcher == null ? "{}" : requestMatcher));
                    break;
                case ALL:
                    mockServerLog.clear(requestMatcher);
                    mockServerMatcher.clear(requestMatcher);
                    mockServerLogger.info(CLEARED, requestMatcher, "clearing expectations and request logs that match:{}", (requestMatcher == null ? "{}" : requestMatcher));
                    break;
            }
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("\"" + request.getFirstQueryStringParameter("type") + "\" is not a valid value for \"type\" parameter, only the following values are supported " + Lists.transform(Arrays.asList(ClearType.values()), new Function<ClearType, String>() {
                public String apply(ClearType input) {
                    return input.name().toLowerCase();
                }
            }));
        }
    }

    public void reset() {
        mockServerMatcher.reset();
        mockServerLog.reset();
        mockServerLogger.info(CLEARED, "resetting all expectations and request logs");
    }

    public void add(Expectation... expectations) {
        for (Expectation expectation : expectations) {
            if (expectation.getHttpRequest() != null) {
                final String hostHeader = expectation.getHttpRequest().getFirstHeader(HOST.toString());
                if (!Strings.isNullOrEmpty(hostHeader)) {
                    scheduler.submit(new Runnable() {
                        @Override
                        public void run() {
                            addSubjectAlternativeName(hostHeader);
                        }
                    });
                }
            }
            mockServerMatcher.add(expectation);
            mockServerLogger.info(CREATED_EXPECTATION, expectation.getHttpRequest(), "creating expectation:{}", expectation.clone());
        }
    }

    public Expectation firstMatchingExpectation(HttpRequest request) {
        if (mockServerMatcher.isEmpty()) {
            mockServerLogger.info(EXPECTATION_NOT_MATCHED, request, "no active expectations");
            return null;
        } else {
            return mockServerMatcher.firstMatchingExpectation(request);
        }
    }

    public void log(LogEntry logEntry) {
        if (mockServerLog != null) {
            mockServerLog.add(logEntry);
        }
    }

    public HttpResponse retrieve(HttpRequest request) {
        HttpResponse response = response();
        if (request != null) {
            HttpRequest httpRequest = null;
            if (!Strings.isNullOrEmpty(request.getBodyAsString())) {
                httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
            }
            try {
                Format format = Format.valueOf(StringUtils.defaultIfEmpty(request.getFirstQueryStringParameter("format").toUpperCase(), "JSON"));
                RetrieveType retrieveType = RetrieveType.valueOf(StringUtils.defaultIfEmpty(request.getFirstQueryStringParameter("type").toUpperCase(), "REQUESTS"));
                switch (retrieveType) {
                    case LOGS: {
                        mockServerLogger.info(RETRIEVED, httpRequest, "retrieving " + retrieveType.name().toLowerCase() + " that match:{}", (httpRequest == null ? request() : httpRequest));
                        List<MessageLogEntry> retrievedMessages = mockServerLog.retrieveMessages(httpRequest);
                        StringBuilder stringBuffer = new StringBuilder();
                        for (int i = 0; i < retrievedMessages.size(); i++) {
                            MessageLogEntry messageLogEntry = retrievedMessages.get(i);
                            stringBuffer
                                .append(messageLogEntry.getTimestamp())
                                .append(" - ")
                                .append(messageLogEntry.getMessage());
                            if (i < retrievedMessages.size() - 1) {
                                stringBuffer.append(LOG_SEPARATOR);
                            }
                        }
                        stringBuffer.append(NEW_LINE);
                        response.withBody(stringBuffer.toString(), PLAIN_TEXT_UTF_8);
                        break;
                    }
                    case REQUESTS: {
                        mockServerLogger.info(RETRIEVED, httpRequest, "retrieving " + retrieveType.name().toLowerCase() + " in " + format.name().toLowerCase() + " that match:{}", (httpRequest == null ? request() : httpRequest));
                        List<HttpRequest> httpRequests = mockServerLog.retrieveRequests(httpRequest);
                        switch (format) {
                            case JAVA:
                                response.withBody(httpRequestToJavaSerializer.serialize(httpRequests), create("application", "java").withCharset(UTF_8));
                                break;
                            case JSON:
                                response.withBody(httpRequestSerializer.serialize(httpRequests), JSON_UTF_8);
                                break;
                            case LOG_ENTRIES:
                                response.withBody(logEntrySerializer.serialize(mockServerLog.retrieveRequestLogEntries(httpRequest)), JSON_UTF_8);
                                break;
                        }
                        break;
                    }
                    case RECORDED_EXPECTATIONS: {
                        mockServerLogger.info(RETRIEVED, httpRequest, "retrieving " + retrieveType.name().toLowerCase() + " in " + format.name().toLowerCase() + " that match:{}", (httpRequest == null ? request() : httpRequest));
                        List<Expectation> expectations = mockServerLog.retrieveExpectations(httpRequest);
                        switch (format) {
                            case JAVA:
                                response.withBody(expectationToJavaSerializer.serialize(expectations), create("application", "java").withCharset(UTF_8));
                                break;
                            case JSON:
                            case LOG_ENTRIES:
                                response.withBody(expectationSerializer.serialize(expectations), JSON_UTF_8);
                                break;
                        }
                        break;
                    }
                    case ACTIVE_EXPECTATIONS: {
                        mockServerLogger.info(RETRIEVED, httpRequest, "retrieving " + retrieveType.name().toLowerCase() + " in " + format.name().toLowerCase() + " that match:{}", (httpRequest == null ? request() : httpRequest));
                        List<Expectation> expectations = mockServerMatcher.retrieveExpectations(httpRequest);
                        switch (format) {
                            case JAVA:
                                response.withBody(expectationToJavaSerializer.serialize(expectations), create("application", "java").withCharset(UTF_8));
                                break;
                            case JSON:
                            case LOG_ENTRIES:
                                response.withBody(expectationSerializer.serialize(expectations), JSON_UTF_8);
                                break;
                        }
                        break;
                    }
                }
            } catch (IllegalArgumentException iae) {
                if (iae.getMessage().contains(RetrieveType.class.getSimpleName())) {
                    throw new IllegalArgumentException("\"" + request.getFirstQueryStringParameter("type") + "\" is not a valid value for \"type\" parameter, only the following values are supported " + Lists.transform(Arrays.asList(RetrieveType.values()), new Function<RetrieveType, String>() {
                        public String apply(RetrieveType input) {
                            return input.name().toLowerCase();
                        }
                    }));
                } else {
                    throw new IllegalArgumentException("\"" + request.getFirstQueryStringParameter("format") + "\" is not a valid value for \"format\" parameter, only the following values are supported " + Lists.transform(Arrays.asList(Format.values()), new Function<Format, String>() {
                        public String apply(Format input) {
                            return input.name().toLowerCase();
                        }
                    }));
                }
            }
        }
        return response.withStatusCode(200);
    }

    public String verify(Verification verification) {
        return mockServerLog.verify(verification);
    }

    public String verify(VerificationSequence verification) {
        return mockServerLog.verify(verification);
    }

    public boolean handle(HttpRequest request, ResponseWriter responseWriter, boolean warDeployment) {
        mockServerLogger.trace(request, "received request:{}", request);

        if (request.matches("PUT", PATH_PREFIX + "/expectation", "/expectation")) {

            for (Expectation expectation : expectationSerializer.deserializeArray(request.getBodyAsString())) {
                if (!warDeployment || validateSupportedFeatures(expectation, request, responseWriter)) {
                    add(expectation);
                }
            }
            responseWriter.writeResponse(request, CREATED);

        } else if (request.matches("PUT", PATH_PREFIX + "/clear", "/clear")) {

            clear(request);
            responseWriter.writeResponse(request, OK);

        } else if (request.matches("PUT", PATH_PREFIX + "/reset", "/reset")) {

            reset();
            responseWriter.writeResponse(request, OK);

        } else if (request.matches("PUT", PATH_PREFIX + "/retrieve", "/retrieve")) {

            responseWriter.writeResponse(request, retrieve(request), true);

        } else if (request.matches("PUT", PATH_PREFIX + "/verify", "/verify")) {

            Verification verification = verificationSerializer.deserialize(request.getBodyAsString());
            mockServerLogger.info(VERIFICATION, verification.getHttpRequest(), "verifying requests that match:{}", verification);
            String result = verify(verification);
            if (StringUtils.isEmpty(result)) {
                responseWriter.writeResponse(request, ACCEPTED);
            } else {
                responseWriter.writeResponse(request, NOT_ACCEPTABLE, result, create("text", "plain").toString());
            }

        } else if (request.matches("PUT", PATH_PREFIX + "/verifySequence", "/verifySequence")) {

            VerificationSequence verificationSequence = verificationSequenceSerializer.deserialize(request.getBodyAsString());
            mockServerLogger.info(VERIFICATION, verificationSequence.getHttpRequests(), "verifying sequence that match:{}", verificationSequence);
            String result = verify(verificationSequence);
            if (StringUtils.isEmpty(result)) {
                responseWriter.writeResponse(request, ACCEPTED);
            } else {
                responseWriter.writeResponse(request, NOT_ACCEPTABLE, result, create("text", "plain").toString());
            }

        } else {
            return false;
        }
        return true;
    }

    private boolean validateSupportedFeatures(Expectation expectation, HttpRequest request, ResponseWriter responseWriter) {
        boolean valid = true;
        Action action = expectation.getAction();
        String NOT_SUPPORTED_MESSAGE = " is not supported by MockServer deployed as a WAR due to limitations in the JEE specification; use mockserver-netty to enable these features";
        if (action instanceof HttpResponse && ((HttpResponse) action).getConnectionOptions() != null) {
            responseWriter.writeResponse(request, response("ConnectionOptions" + NOT_SUPPORTED_MESSAGE), true);
            valid = false;
        } else if (action instanceof HttpObjectCallback) {
            responseWriter.writeResponse(request, response("HttpObjectCallback" + NOT_SUPPORTED_MESSAGE), true);
            valid = false;
        } else if (action instanceof HttpError) {
            responseWriter.writeResponse(request, response("HttpError" + NOT_SUPPORTED_MESSAGE), true);
            valid = false;
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
}
