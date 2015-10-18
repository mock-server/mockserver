package org.mockserver.server;

import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import io.netty.handler.codec.http.HttpHeaders;
import org.mockserver.client.serialization.*;
import org.mockserver.filters.LogFilter;
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
    private LogFilter logFilter = new LogFilter();
    private ActionHandler actionHandler = new ActionHandler(logFilter);
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
    public void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    @Override
    public void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    @Override
    protected void doHead(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    @Override
    protected void doDelete(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    @Override
    protected void doOptions(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    @Override
    protected void doTrace(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    @Override
    public void doPut(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {

        try {
            String requestPath = retrieveRequestPath(httpServletRequest);
            if (requestPath.equals("/status")) {

                httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
                httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
                IOStreamUtils.writeToOutputStream(portBindingSerializer.serialize(portBinding(httpServletRequest.getLocalPort())).getBytes(), httpServletResponse);

            } else if (requestPath.equals("/bind")) {

                httpServletResponse.setStatus(HttpStatusCode.NOT_IMPLEMENTED_501.code());

            } else if (requestPath.equals("/expectation")) {

                Expectation expectation = expectationSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest));

                Action action = expectation.getAction(false);
                if (validateSupportedFeatures(action, httpServletResponse)) {
                    mockServerMatcher.when(expectation.getHttpRequest(), expectation.getTimes(), expectation.getTimeToLive()).thenRespond(expectation.getHttpResponse(false)).thenForward(expectation.getHttpForward()).thenCallback(expectation.getHttpCallback());
                    httpServletResponse.setStatus(HttpStatusCode.CREATED_201.code());
                }

            } else if (requestPath.equals("/clear")) {

                HttpRequest httpRequest = httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest));
                logFilter.clear(httpRequest);
                mockServerMatcher.clear(httpRequest);
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());

            } else if (requestPath.equals("/reset")) {

                logFilter.reset();
                mockServerMatcher.reset();
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());

            } else if (requestPath.equals("/dumpToLog")) {

                mockServerMatcher.dumpToLog(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());

            } else if (requestPath.equals("/retrieve")) {

                if ("expectation".equals(httpServletRequest.getParameter("type"))) {
                    Expectation[] expectations = mockServerMatcher.retrieve(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
                    httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
                    httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
                    IOStreamUtils.writeToOutputStream(expectationSerializer.serialize(expectations).getBytes(), httpServletResponse);
                } else {
                    HttpRequest[] requests = logFilter.retrieve(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
                    httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
                    httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
                    IOStreamUtils.writeToOutputStream(httpRequestSerializer.serialize(requests).getBytes(), httpServletResponse);
                }

            } else if (requestPath.equals("/verify")) {

                String result = logFilter.verify(verificationSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
                if (result.isEmpty()) {
                    httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                } else {
                    httpServletResponse.setStatus(HttpStatusCode.NOT_ACCEPTABLE_406.code());
                    httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString());
                    IOStreamUtils.writeToOutputStream(result.getBytes(), httpServletResponse);
                }

            } else if (requestPath.equals("/verifySequence")) {

                String result = logFilter.verify(verificationSequenceSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
                if (result.isEmpty()) {
                    httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                } else {
                    httpServletResponse.setStatus(HttpStatusCode.NOT_ACCEPTABLE_406.code());
                    httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString());
                    IOStreamUtils.writeToOutputStream(result.getBytes(), httpServletResponse);
                }

            } else if (requestPath.equals("/stop")) {

                httpServletResponse.setStatus(HttpStatusCode.NOT_IMPLEMENTED_501.code());

            } else {

                mockResponse(httpServletRequest, httpServletResponse);

            }
        } catch (Exception e) {
            logger.error("Exception processing " + httpServletRequest, e);
            httpServletResponse.setStatus(HttpStatusCode.BAD_REQUEST_400.code());
        }
    }

    private String retrieveRequestPath(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getPathInfo() != null && httpServletRequest.getContextPath() != null ? httpServletRequest.getPathInfo() : httpServletRequest.getRequestURI();
    }

    private void mockResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        HttpRequest httpRequest = httpServletRequestToMockServerRequestDecoder.mapHttpServletRequestToMockServerRequest(httpServletRequest);

        Action action = mockServerMatcher.handle(httpRequest);
        if (validateSupportedFeatures(action, httpServletResponse)) {
            mapResponse(actionHandler.processAction(action, httpRequest), httpServletResponse);
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
