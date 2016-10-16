package org.mockserver.server;

import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import io.netty.handler.codec.http.HttpHeaders;
import org.mockserver.client.serialization.*;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.mappers.HttpServletRequestToMockServerRequestDecoder;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.*;
import org.mockserver.streams.IOStreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.Charset;

import static org.mockserver.configuration.ConfigurationProperties.enableCORS;
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

            if (enableCORS() && request.getMethod().getValue().equals("OPTIONS") && !request.getFirstHeader("Origin").isEmpty()) {

                httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
                addCORSHeaders(httpServletResponse);

            } else if (request.matches("PUT", "/status")) {

                httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
                httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
                IOStreamUtils.writeToOutputStream(portBindingSerializer.serialize(portBinding(httpServletRequest.getLocalPort())).getBytes(), httpServletResponse);
                addCORSHeaders(httpServletResponse);

            } else if (request.matches("PUT", "/bind")) {

                httpServletResponse.setStatus(HttpStatusCode.NOT_IMPLEMENTED_501.code());
                addCORSHeaders(httpServletResponse);

            } else if (request.matches("PUT", "/expectation")) {

                Expectation expectation = expectationSerializer.deserialize(request.getBodyAsString());

                addCORSHeaders(httpServletResponse);
                Action action = expectation.getAction(false);
                if (validateSupportedFeatures(action, httpServletResponse)) {
                    mockServerMatcher.when(expectation.getHttpRequest(), expectation.getTimes(), expectation.getTimeToLive()).thenRespond(expectation.getHttpResponse(false)).thenForward(expectation.getHttpForward()).thenCallback(expectation.getHttpCallback());
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
                addCORSHeaders(httpServletResponse);

            } else if (request.matches("PUT", "/reset")) {

                requestLogFilter.reset();
                mockServerMatcher.reset();
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                addCORSHeaders(httpServletResponse);

            } else if (request.matches("PUT", "/dumpToLog")) {

                mockServerMatcher.dumpToLog(httpRequestSerializer.deserialize(request.getBodyAsString()));
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                addCORSHeaders(httpServletResponse);

            } else if (request.matches("PUT", "/retrieve")) {

                addCORSHeaders(httpServletResponse);
                if (request.hasQueryStringParameter("type", "expectation")) {
                    Expectation[] expectations = mockServerMatcher.retrieve(httpRequestSerializer.deserialize(request.getBodyAsString()));
                    httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
                    httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
                    IOStreamUtils.writeToOutputStream(expectationSerializer.serialize(expectations).getBytes(), httpServletResponse);
                } else {
                    HttpRequest[] requests = requestLogFilter.retrieve(httpRequestSerializer.deserialize(request.getBodyAsString()));
                    httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
                    httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
                    IOStreamUtils.writeToOutputStream(httpRequestSerializer.serialize(requests).getBytes(), httpServletResponse);
                }

            } else if (request.matches("PUT", "/verify")) {

                String result = requestLogFilter.verify(verificationSerializer.deserialize(request.getBodyAsString()));
                addCORSHeaders(httpServletResponse);
                verifyResponse(httpServletResponse, result);

            } else if (request.matches("PUT", "/verifySequence")) {

                String result = requestLogFilter.verify(verificationSequenceSerializer.deserialize(request.getBodyAsString()));
                addCORSHeaders(httpServletResponse);
                verifyResponse(httpServletResponse, result);

            } else if (request.matches("PUT", "/stop")) {

                httpServletResponse.setStatus(HttpStatusCode.NOT_IMPLEMENTED_501.code());
                addCORSHeaders(httpServletResponse);

            } else {

                Action action = mockServerMatcher.handle(request);
                if (validateSupportedFeatures(action, httpServletResponse)) {
                    mapResponse(actionHandler.processAction(action, request), httpServletResponse);
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
            httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString());
            IOStreamUtils.writeToOutputStream(result.getBytes(), httpServletResponse);
        }
    }

    private void addCORSHeaders(HttpServletResponse httpServletResponse) {
        if (enableCORS()) {
            httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
            httpServletResponse.setHeader("Access-Control-Allow-Methods", "PUT");
            httpServletResponse.setHeader("X-CORS", "MockServer CORS support enabled by default, to disable ConfigurationProperties.enableCORS(false) or -Dmockserver.disableCORS=false");
        }
    }

    private boolean validateSupportedFeatures(Action action, HttpServletResponse httpServletResponse) {
        boolean valid = true;
        if (action instanceof HttpResponse && ((HttpResponse) action).getConnectionOptions() != null) {
            writeNotSupportedResponse(ConnectionOptions.class, httpServletResponse);
            valid = false;
        } else if (action instanceof HttpError) {
            writeNotSupportedResponse(HttpError.class, httpServletResponse);
            valid = false;
        }
        return valid;
    }

    private void writeNotSupportedResponse(Class<? extends ObjectWithJsonToString> notSupportedFeature, HttpServletResponse httpServletResponse) {
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
        if (response.getBody() != null && Strings.isNullOrEmpty(response.getFirstHeader(HttpHeaders.Names.CONTENT_TYPE))) {
            Charset bodyCharset = response.getBody().getCharset(null);
            String bodyContentType = response.getBody().getContentType();
            if (bodyCharset != null) {
                response.updateHeader(header(HttpHeaders.Names.CONTENT_TYPE, bodyContentType + "; charset=" + bodyCharset.name().toLowerCase()));
            } else if (bodyContentType != null) {
                response.updateHeader(header(HttpHeaders.Names.CONTENT_TYPE, bodyContentType));
            }
        }
    }
}
