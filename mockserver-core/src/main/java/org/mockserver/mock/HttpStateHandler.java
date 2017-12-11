package org.mockserver.mock;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.client.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.client.serialization.java.HttpRequestToJavaSerializer;
import org.mockserver.filters.LogFilter;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.model.*;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.socket.KeyAndCertificateFactory;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static com.google.common.net.MediaType.*;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class HttpStateHandler {

    // mockserver
    private LoggingFormatter logFormatter = new LoggingFormatter(LoggerFactory.getLogger(this.getClass()), this);
    private LogFilter logFilter = new LogFilter(logFormatter);
    private MockServerMatcher mockServerMatcher = new MockServerMatcher(logFormatter);
    private WebSocketClientRegistry webSocketClientRegistry = new WebSocketClientRegistry();
    // serializers
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestToJavaSerializer httpRequestToJavaSerializer = new HttpRequestToJavaSerializer();
    private ExpectationToJavaSerializer expectationToJavaSerializer = new ExpectationToJavaSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();

    public LoggingFormatter getLogFormatter() {
        return logFormatter;
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
                    logFilter.clear(requestMatcher);
                    logFormatter.infoLog(requestMatcher, "clearing request logs that match:{}", requestMatcher);
                    break;
                case EXPECTATIONS:
                    mockServerMatcher.clear(requestMatcher);
                    logFormatter.infoLog(requestMatcher, "clearing expectations that match:{}", requestMatcher);
                    break;
                case ALL:
                    logFilter.clear(requestMatcher);
                    mockServerMatcher.clear(requestMatcher);
                    logFormatter.infoLog(requestMatcher, "clearing expectations and request logs that match:{}", requestMatcher);
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
        logFilter.reset();
        logFormatter.infoLog(request(), "resetting all expectations and request logs");
    }

    public void add(Expectation... expectations) {
        for (Expectation expectation : expectations) {
            KeyAndCertificateFactory.addSubjectAlternativeName(expectation.getHttpRequest().getFirstHeader(HOST.toString()));
            mockServerMatcher.add(expectation);
            logFormatter.infoLog(expectation.getHttpRequest(), "creating expectation:{}", expectation);
        }
    }

    public Expectation firstMatchingExpectation(HttpRequest request) {
        if (mockServerMatcher.httpRequestMatchers.isEmpty()) {
            logFormatter.infoLog(request(), "no active expectations when receiving request:{}", request);
        }
        return mockServerMatcher.firstMatchingExpectation(request);
    }

    public void log(LogEntry logEntry) {
        logFilter.onRequest(logEntry);
    }

    public HttpResponse retrieve(HttpRequest request) {
        HttpRequest httpRequest = null;
        if (!Strings.isNullOrEmpty(request.getBodyAsString())) {
            httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
        }
        HttpResponse response = response();
        try {
            Format format = Format.valueOf(StringUtils.defaultIfEmpty(request.getFirstQueryStringParameter("format").toUpperCase(), "JSON"));
            RetrieveType retrieveType = RetrieveType.valueOf(StringUtils.defaultIfEmpty(request.getFirstQueryStringParameter("type").toUpperCase(), "REQUESTS"));
            switch (retrieveType) {
                case LOGS: {
                    logFormatter.infoLog(httpRequest, "retrieving " + retrieveType.name().toLowerCase() + " that match:{}", (httpRequest == null ? request() : httpRequest));
                    StringBuilder stringBuffer = new StringBuilder();
                    List<String> retrieveMessages = logFilter.retrieveMessages(httpRequest);
                    for (int i = 0; i < retrieveMessages.size(); i++) {
                        stringBuffer.append(retrieveMessages.get(i));
                        if (i < retrieveMessages.size() - 1) {
                            stringBuffer.append("------------------------------------\n");
                        }
                    }
                    stringBuffer.append("\n");
                    response.withBody(stringBuffer.toString(), PLAIN_TEXT_UTF_8);
                    break;
                }
                case REQUESTS: {
                    logFormatter.infoLog(httpRequest, "retrieving " + retrieveType.name().toLowerCase() + " in " + format.name().toLowerCase() + " that match:{}", (httpRequest == null ? request() : httpRequest));
                    List<HttpRequest> httpRequests = logFilter.retrieveRequests(httpRequest);
                    switch (format) {
                        case JAVA:
                            response.withBody(httpRequestToJavaSerializer.serialize(httpRequests), create("application", "java").withCharset(UTF_8));
                            break;
                        case JSON:
                            response.withBody(httpRequestSerializer.serialize(httpRequests), JSON_UTF_8);
                            break;
                    }
                    break;
                }
                case RECORDED_EXPECTATIONS: {
                    logFormatter.infoLog(httpRequest, "retrieving " + retrieveType.name().toLowerCase() + " in " + format.name().toLowerCase() + " that match:{}", (httpRequest == null ? request() : httpRequest));
                    List<Expectation> expectations = logFilter.retrieveExpectations(httpRequest);
                    switch (format) {
                        case JAVA:
                            response.withBody(expectationToJavaSerializer.serialize(expectations), create("application", "java").withCharset(UTF_8));
                            break;
                        case JSON:
                            response.withBody(expectationSerializer.serialize(expectations), JSON_UTF_8);
                            break;
                    }
                    break;
                }
                case ACTIVE_EXPECTATIONS: {
                    logFormatter.infoLog(httpRequest, "retrieving " + retrieveType.name().toLowerCase() + " in " + format.name().toLowerCase() + " that match:{}", (httpRequest == null ? request() : httpRequest));
                    List<Expectation> expectations = mockServerMatcher.retrieveExpectations(httpRequest);
                    switch (format) {
                        case JAVA:
                            response.withBody(expectationToJavaSerializer.serialize(expectations), create("application", "java").withCharset(UTF_8));
                            break;
                        case JSON:
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

        return response.withStatusCode(200);
    }

    public String verify(Verification verification) {
        return logFilter.verify(verification);
    }

    public String verify(VerificationSequence verification) {
        return logFilter.verify(verification);
    }

    public boolean handle(HttpRequest request, ResponseWriter responseWriter, boolean warDeployment) {
        logFormatter.traceLog("received request:{}", request);

        if ((enableCORSForAPI() || enableCORSForAllResponses()) && request.getMethod().getValue().equals("OPTIONS") && !request.getFirstHeader("Origin").isEmpty()) {

            responseWriter.writeResponse(request, OK);

        } else if (request.matches("PUT", "/expectation")) {

            for (Expectation expectation : expectationSerializer.deserializeArray(request.getBodyAsString())) {
                if (!warDeployment || validateSupportedFeatures(expectation, request, responseWriter)) {
                    add(expectation);
                }
            }
            responseWriter.writeResponse(request, CREATED);

        } else if (request.matches("PUT", "/clear")) {

            clear(request);
            responseWriter.writeResponse(request, OK);

        } else if (request.matches("PUT", "/reset")) {

            reset();
            responseWriter.writeResponse(request, OK);

        } else if (request.matches("PUT", "/retrieve")) {

            responseWriter.writeResponse(request, retrieve(request));

        } else if (request.matches("PUT", "/verify")) {

            Verification verification = verificationSerializer.deserialize(request.getBodyAsString());
            String result = verify(verification);
            if (StringUtils.isEmpty(result)) {
                responseWriter.writeResponse(request, ACCEPTED);
            } else {
                responseWriter.writeResponse(request, NOT_ACCEPTABLE, result, create("text", "plain").toString());
            }
            logFormatter.infoLog(request, "verifying requests that match:{}", verification);

        } else if (request.matches("PUT", "/verifySequence")) {

            VerificationSequence verificationSequence = verificationSequenceSerializer.deserialize(request.getBodyAsString());
            String result = verify(verificationSequence);
            if (StringUtils.isEmpty(result)) {
                responseWriter.writeResponse(request, ACCEPTED);
            } else {
                responseWriter.writeResponse(request, NOT_ACCEPTABLE, result, create("text", "plain").toString());
            }
            logFormatter.infoLog(request, "verifying sequence that match:{}", verificationSequence);

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
            responseWriter.writeResponse(request, response("ConnectionOptions" + NOT_SUPPORTED_MESSAGE));
            valid = false;
        } else if (action instanceof HttpObjectCallback) {
            responseWriter.writeResponse(request, response("HttpObjectCallback" + NOT_SUPPORTED_MESSAGE));
            valid = false;
        } else if (action instanceof HttpError) {
            responseWriter.writeResponse(request, response("HttpError" + NOT_SUPPORTED_MESSAGE));
            valid = false;
        }
        return valid;
    }

    public WebSocketClientRegistry getWebSocketClientRegistry() {
        return webSocketClientRegistry;
    }

}
