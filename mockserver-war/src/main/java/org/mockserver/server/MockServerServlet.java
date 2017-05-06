package org.mockserver.server;

import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import org.mockserver.client.serialization.*;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.model.*;
import org.mockserver.streams.IOStreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.Charset;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.PortBinding.portBinding;

/**
 * @author jamesdbloom
 */
public class MockServerServlet extends HttpServlet {

    private static final String NOT_SUPPORTED_MESSAGE = " is not supported by MockServer deployable WAR due to limitations in the JEE specification; use mockserver-netty to enable these features";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
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

    @Override
    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {

        HttpRequest request = null;
        try {
            request = httpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest);

            if ((enableCORSForAPI() || enableCORSForAllResponses()) && request.getMethod().getValue().equals("OPTIONS") && !request.getFirstHeader("Origin").isEmpty()) {

                httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.getPath().getValue().equals("/_mockserver_callback_websocket")) {

                writeNotSupportedResponse(ExpectationCallback.class, httpServletResponse);

            } else if (request.matches("PUT", "/status")) {

                httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
                httpServletResponse.setHeader(CONTENT_TYPE.toString(), MediaType.JSON_UTF_8.toString());
                IOStreamUtils.writeToOutputStream(portBindingSerializer.serialize(portBinding(httpServletRequest.getLocalPort())).getBytes(), httpServletResponse);
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/bind")) {

                httpServletResponse.setStatus(HttpStatusCode.NOT_IMPLEMENTED_501.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/expectation")) {

                Expectation expectation = expectationSerializer.deserialize(request.getBodyAsString());

                addCORSHeadersForAPI(httpServletResponse);
                Action action = expectation.getAction();
                if (validateSupportedFeatures(action, httpServletResponse)) {
                    mockServerMatcher.when(expectation.getHttpRequest(), expectation.getTimes(), expectation.getTimeToLive()).thenRespond(expectation.getHttpResponse()).thenForward(expectation.getHttpForward()).thenCallback(expectation.getHttpClassCallback());
                    httpServletResponse.setStatus(HttpStatusCode.CREATED_201.code());
                }

            } else if (request.matches("PUT", "/clear")) {

                HttpRequest httpRequest = httpRequestSerializer.deserialize(request.getBodyAsString());
                if (request.hasQueryStringParameter("type", "expectation")) {
                    mockServerMatcher.clear(httpRequest);
                } else if (request.hasQueryStringParameter("type", "log")) {
                    requestLogFilter.clear(httpRequest);
                } else {
                    requestLogFilter.clear(httpRequest);
                    mockServerMatcher.clear(httpRequest);
                }
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/reset")) {

                requestLogFilter.reset();
                mockServerMatcher.reset();
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/dumpToLog")) {

                mockServerMatcher.dumpToLog(httpRequestSerializer.deserialize(request.getBodyAsString()));
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else if (request.matches("PUT", "/retrieve")) {

                addCORSHeadersForAPI(httpServletResponse);
                if (request.hasQueryStringParameter("type", "expectation")) {
                    Expectation[] expectations = mockServerMatcher.retrieveExpectations(httpRequestSerializer.deserialize(request.getBodyAsString()));
                    httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
                    httpServletResponse.setHeader(CONTENT_TYPE.toString(), MediaType.JSON_UTF_8.toString());
                    IOStreamUtils.writeToOutputStream(expectationSerializer.serialize(expectations).getBytes(), httpServletResponse);
                } else {
                    HttpRequest[] requests = requestLogFilter.retrieve(httpRequestSerializer.deserialize(request.getBodyAsString()));
                    httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
                    httpServletResponse.setHeader(CONTENT_TYPE.toString(), MediaType.JSON_UTF_8.toString());
                    IOStreamUtils.writeToOutputStream(httpRequestSerializer.serialize(requests).getBytes(), httpServletResponse);
                }

            } else if (request.matches("PUT", "/verify")) {

                String result = requestLogFilter.verify(verificationSerializer.deserialize(request.getBodyAsString()));
                addCORSHeadersForAPI(httpServletResponse);
                verifyResponse(httpServletResponse, result);

            } else if (request.matches("PUT", "/verifySequence")) {

                String result = requestLogFilter.verify(verificationSequenceSerializer.deserialize(request.getBodyAsString()));
                addCORSHeadersForAPI(httpServletResponse);
                verifyResponse(httpServletResponse, result);

            } else if (request.matches("PUT", "/stop")) {

                httpServletResponse.setStatus(HttpStatusCode.NOT_IMPLEMENTED_501.code());
                addCORSHeadersForAPI(httpServletResponse);

            } else {

                Action action = mockServerMatcher.retrieveAction(request);
                if (validateSupportedFeatures(action, httpServletResponse)) {
                    mapResponse(actionHandler.processAction(action, request), httpServletResponse);
                    addCORSHeadersForAllResponses(httpServletResponse);
                }

            }
        } catch (Exception e) {
            logger.error("Exception processing " + (request != null ? request : httpServletRequest), e);
            httpServletResponse.setStatus(HttpStatusCode.BAD_REQUEST_400.code());
        }
    }

    private void verifyResponse(HttpServletResponse httpServletResponse, String result) {
        if (result.isEmpty()) {
            httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
        } else {
            httpServletResponse.setStatus(HttpStatusCode.NOT_ACCEPTABLE_406.code());
            httpServletResponse.setHeader(CONTENT_TYPE.toString(), MediaType.PLAIN_TEXT_UTF_8.toString());
            IOStreamUtils.writeToOutputStream(result.getBytes(), httpServletResponse);
        }
    }

    private void addCORSHeadersForAPI(HttpServletResponse httpServletResponse) {
        if (enableCORSForAPI()) {
            addCORSHeaders(httpServletResponse);
        } else {
            addCORSHeadersForAllResponses(httpServletResponse);
        }
    }

    private void addCORSHeadersForAllResponses(HttpServletResponse httpServletResponse) {
        if (enableCORSForAllResponses()) {
            addCORSHeaders(httpServletResponse);
        }
    }

    private void addCORSHeaders(HttpServletResponse httpServletResponse) {
        String methods = "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE";
        String headers = "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary";
        if (httpServletResponse.getHeaders("Access-Control-Allow-Origin").isEmpty()) {
            httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
        }
        if (httpServletResponse.getHeaders("Access-Control-Allow-Methods").isEmpty()) {
            httpServletResponse.setHeader("Access-Control-Allow-Methods", methods);
        }
        if (httpServletResponse.getHeaders("Access-Control-Allow-Headers").isEmpty()) {
            httpServletResponse.setHeader("Access-Control-Allow-Headers", headers);
        }
        if (httpServletResponse.getHeaders("Access-Control-Expose-Headers").isEmpty()) {
            httpServletResponse.setHeader("Access-Control-Expose-Headers", headers);
        }
        if (httpServletResponse.getHeaders("Access-Control-Max-Age").isEmpty()) {
            httpServletResponse.setHeader("Access-Control-Max-Age", "1");
        }
        if (httpServletResponse.getHeaders("X-CORS").isEmpty()) {
            httpServletResponse.setHeader("X-CORS", "MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORSForAPI(false) or -Dmockserver.disableCORS=false");
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
