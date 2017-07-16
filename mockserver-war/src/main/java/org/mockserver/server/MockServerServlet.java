package org.mockserver.server;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import org.mockserver.client.serialization.*;
import org.mockserver.cors.CORSHeaders;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.logging.LogFormatter;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.model.*;
import org.mockserver.socket.KeyAndCertificateFactory;
import org.mockserver.streams.IOStreamUtils;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.Charset;

import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpStatusCode.*;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class MockServerServlet extends HttpServlet {

    private static final String NOT_SUPPORTED_MESSAGE = " is not supported by MockServer deployable WAR due to limitations in the JEE specification; use mockserver-netty to enable these features";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private LogFormatter logFormatter = new LogFormatter(logger);
    // mockserver
    private MockServerMatcher mockServerMatcher = new MockServerMatcher();
    private RequestLogFilter requestLogFilter = new RequestLogFilter();
    private ActionHandler actionHandler = new ActionHandler(requestLogFilter);
    // mappers
    private HttpServletRequestToMockServerRequestDecoder httpServletRequestToMockServerRequestDecoder = new HttpServletRequestToMockServerRequestDecoder();
    private MockServerResponseToHttpServletResponseEncoder mockServerResponseToHttpServletResponseEncoder = new MockServerResponseToHttpServletResponseEncoder();
    // serializers
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private PortBindingSerializer portBindingSerializer = new PortBindingSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();
    // CORS
    private CORSHeaders addCORSHeaders = new CORSHeaders();

    @Override
    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {

        HttpRequest request = null;
        try {
            request = httpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest);

            if ((enableCORSForAPI() || enableCORSForAllResponses()) && request.getMethod().getValue().equals("OPTIONS") && !request.getFirstHeader("Origin").isEmpty()) {

                httpServletResponse.setStatus(OK_200.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.getPath().getValue().equals("/_mockserver_callback_websocket")) {

                writeNotSupportedResponse(ExpectationCallback.class, httpServletResponse);

            } else if (request.matches("PUT", "/status")) {

                httpServletResponse.setStatus(OK_200.code());
                httpServletResponse.setHeader(CONTENT_TYPE.toString(), JSON_UTF_8.toString());
                IOStreamUtils.writeToOutputStream(portBindingSerializer.serialize(portBinding(httpServletRequest.getLocalPort())).getBytes(), httpServletResponse);
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/bind")) {

                httpServletResponse.setStatus(NOT_IMPLEMENTED_501.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/expectation")) {

                for (Expectation expectation : expectationSerializer.deserializeArray(request.getBodyAsString())) {
                    Action action = expectation.getAction();
                    if (validateSupportedFeatures(action, httpServletResponse)) {
                        KeyAndCertificateFactory.addSubjectAlternativeName(expectation.getHttpRequest().getFirstHeader(HOST.toString()));
                        mockServerMatcher
                                .when(expectation.getHttpRequest(), expectation.getTimes(), expectation.getTimeToLive())
                                .thenRespond(expectation.getHttpResponse())
                                .thenForward(expectation.getHttpForward())
                                .thenCallback(expectation.getHttpClassCallback());
                        logFormatter.infoLog("creating expectation:{}", expectation);
                    }
                }
                httpServletResponse.setStatus(CREATED_201.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/clear")) {

                HttpRequest httpRequest = null;
                if (!Strings.isNullOrEmpty(request.getBodyAsString())) {
                    httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
                }
                if (request.hasQueryStringParameter("type", "expectation")) {
                    logFormatter.infoLog("clearing expectations that match:{}", httpRequest);
                    mockServerMatcher.clear(httpRequest);
                } else if (request.hasQueryStringParameter("type", "log")) {
                    logFormatter.infoLog("clearing request logs that match:{}", httpRequest);
                    requestLogFilter.clear(httpRequest);
                } else {
                    logFormatter.infoLog("clearing expectations and request logs that match:{}", httpRequest);
                    requestLogFilter.clear(httpRequest);
                    mockServerMatcher.clear(httpRequest);
                }
                httpServletResponse.setStatus(OK_200.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/reset")) {

                requestLogFilter.reset();
                mockServerMatcher.reset();
                logFormatter.infoLog("resetting all expectations and request logs");
                httpServletResponse.setStatus(OK_200.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/dumpToLog")) {

                HttpRequest httpRequest = null;
                if (!Strings.isNullOrEmpty(request.getBodyAsString())) {
                    httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
                }
                mockServerMatcher.dumpToLog(httpRequest);
                httpServletResponse.setStatus(OK_200.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/retrieve")) {

                HttpRequest httpRequest = null;
                if (!Strings.isNullOrEmpty(request.getBodyAsString())) {
                    httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
                }
                if (request.hasQueryStringParameter("type", "expectation")) {
                    Expectation[] expectations = mockServerMatcher.retrieveExpectations(httpRequest);
                    logFormatter.infoLog("retrieving expectations that match:{}", httpRequest);
                    httpServletResponse.setStatus(OK_200.code());
                    httpServletResponse.setHeader(CONTENT_TYPE.toString(), JSON_UTF_8.toString());
                    IOStreamUtils.writeToOutputStream(expectationSerializer.serialize(expectations).getBytes(), httpServletResponse);
                } else {
                    HttpRequest[] requests = requestLogFilter.retrieve(httpRequest);
                    logFormatter.infoLog("retrieving requests that match:{}", httpRequest);
                    httpServletResponse.setStatus(OK_200.code());
                    httpServletResponse.setHeader(CONTENT_TYPE.toString(), JSON_UTF_8.toString());
                    IOStreamUtils.writeToOutputStream(httpRequestSerializer.serialize(requests).getBytes(), httpServletResponse);
                }
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/verify")) {

                Verification verification = verificationSerializer.deserialize(request.getBodyAsString());
                String result = requestLogFilter.verify(verification);
                logFormatter.infoLog("verifying requests that match:{}", verification);
                verifyResponse(httpServletResponse, result);
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/verifySequence")) {

                VerificationSequence verificationSequence = verificationSequenceSerializer.deserialize(request.getBodyAsString());
                String result = requestLogFilter.verify(verificationSequence);
                logFormatter.infoLog("verifying sequence that match:{}", verificationSequence);
                verifyResponse(httpServletResponse, result);
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/stop")) {

                httpServletResponse.setStatus(NOT_IMPLEMENTED_501.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else {

                Action action = mockServerMatcher.retrieveAction(request);
                if (validateSupportedFeatures(action, httpServletResponse)) {
                    HttpResponse response = actionHandler.processAction(action, request);
                    mapResponse(response, httpServletResponse);
                    addCORSHeadersForAllResponses(httpServletResponse);
                }

            }
        } catch (IllegalArgumentException iae) {
            httpServletResponse.setStatus(BAD_REQUEST_400.code());
            httpServletResponse.setHeader(CONTENT_TYPE.toString(), PLAIN_TEXT_UTF_8.toString());
            IOStreamUtils.writeToOutputStream(iae.getMessage().getBytes(Charsets.UTF_8), httpServletResponse);
        } catch (Exception e) {
            logger.error("Exception processing " + (request != null ? request : httpServletRequest), e);
            httpServletResponse.setStatus(BAD_REQUEST_400.code());
        }
    }

    private void verifyResponse(HttpServletResponse httpServletResponse, String result) {
        if (result.isEmpty()) {
            httpServletResponse.setStatus(ACCEPTED_202.code());
        } else {
            httpServletResponse.setStatus(HttpStatusCode.NOT_ACCEPTABLE_406.code());
            httpServletResponse.setHeader(CONTENT_TYPE.toString(), PLAIN_TEXT_UTF_8.toString());
            IOStreamUtils.writeToOutputStream(result.getBytes(), httpServletResponse);
        }
    }

    private void addCORSHeadersForAPI(HttpServletResponse httpServletResponse) {
        if (enableCORSForAPI()) {
            addCORSHeaders.addCORSHeaders(httpServletResponse);
        } else {
            addCORSHeadersForAllResponses(httpServletResponse);
        }
    }

    private void addCORSHeadersForAllResponses(HttpServletResponse httpServletResponse) {
        if (enableCORSForAllResponses()) {
            addCORSHeaders.addCORSHeaders(httpServletResponse);
        }
    }


    private boolean validateSupportedFeatures(Action action, HttpServletResponse httpServletResponse) {
        boolean valid = true;
        if (action instanceof HttpResponse && ((HttpResponse) action).getConnectionOptions() != null) {
            writeNotSupportedResponse(ConnectionOptions.class, httpServletResponse);
            valid = false;
        } else if (action instanceof HttpObjectCallback) {
            writeNotSupportedResponse(HttpObjectCallback.class, httpServletResponse);
            valid = false;
        } else if (action instanceof HttpError) {
            writeNotSupportedResponse(HttpError.class, httpServletResponse);
            valid = false;
        }
        return valid;
    }

    private void writeNotSupportedResponse(Class<?> notSupportedFeature, HttpServletResponse httpServletResponse) {
        httpServletResponse.setStatus(HttpStatusCode.NOT_ACCEPTABLE_406.code());
        IOStreamUtils.writeToOutputStream((notSupportedFeature.getSimpleName() + NOT_SUPPORTED_MESSAGE).getBytes(), httpServletResponse);
    }

    private void mapResponse(HttpResponse httpResponse, HttpServletResponse httpServletResponse) {
        if (httpResponse == null) {
            httpResponse = notFoundResponse();
        }

        addContentTypeHeader(httpResponse);

        mockServerResponseToHttpServletResponseEncoder.mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);
    }

    private void addContentTypeHeader(HttpResponse response) {
        if (response.getBody() != null && Strings.isNullOrEmpty(response.getFirstHeader(CONTENT_TYPE.toString()))) {
            Charset bodyCharset = response.getBody().getCharset(null);
            String bodyContentType = response.getBody().getContentType();
            if (bodyCharset != null) {
                response.updateHeader(header(CONTENT_TYPE.toString(), bodyContentType + "; charset=" + bodyCharset.name().toLowerCase()));
            } else if (bodyContentType != null) {
                response.updateHeader(header(CONTENT_TYPE.toString(), bodyContentType));
            }
        }
    }
}
