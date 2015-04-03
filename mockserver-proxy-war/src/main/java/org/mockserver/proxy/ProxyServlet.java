package org.mockserver.proxy;

import com.google.common.base.Strings;
import io.netty.handler.codec.http.HttpHeaders;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.filters.*;
import org.mockserver.mappers.HttpServletToMockServerRequestMapper;
import org.mockserver.mappers.MockServerToHttpServletResponseMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.streams.IOStreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.OutboundHttpRequest.outboundRequest;

/**
 * @author jamesdbloom
 */
public class ProxyServlet extends HttpServlet {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mockserver
    private Filters filters = new Filters();
    public LogFilter logFilter = new LogFilter();
    // http client
    private NettyHttpClient httpClient = new NettyHttpClient();
    // mappers
    private HttpServletToMockServerRequestMapper httpServletToMockServerRequestMapper = new HttpServletToMockServerRequestMapper();
    private MockServerToHttpServletResponseMapper mockServerToHttpServletResponseMapper = new MockServerToHttpServletResponseMapper();
    // serializers
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();

    public ProxyServlet() {
        filters.withFilter(new HttpRequest(), new HopByHopHeaderFilter());
        filters.withFilter(new HttpRequest(), logFilter);
    }

    /**
     * Add filter for HTTP requests, each filter get called before each request is proxied, if the filter return null then the request is not proxied
     *
     * @param httpRequest the request to match against for this filter
     * @param filter the filter to execute for this request, if the filter returns null the request will not be proxied
     */
    public ProxyServlet withFilter(HttpRequest httpRequest, RequestFilter filter) {
        filters.withFilter(httpRequest, filter);
        return this;
    }

    /**
     * Add filter for HTTP response, each filter get called after each request has been proxied
     *
     * @param httpRequest the request to match against for this filter
     * @param filter the filter that is executed after this request has been proxied
     */
    public ProxyServlet withFilter(HttpRequest httpRequest, ResponseFilter filter) {
        filters.withFilter(httpRequest, filter);
        return this;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        forwardRequest(request, response);
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) {
        forwardRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        forwardRequest(request, response);
    }

    @Override
    protected void doPut(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {

        try {
            String requestPath = httpServletRequest.getPathInfo() != null && httpServletRequest.getContextPath() != null ? httpServletRequest.getPathInfo() : httpServletRequest.getRequestURI();
            if (requestPath.equals("/status")) {

                httpServletResponse.setStatus(HttpStatusCode.OK_200.code());

            } else if (requestPath.equals("/clear")) {

                logFilter.clear(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());

            } else if (requestPath.equals("/reset")) {

                logFilter.reset();
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());

            } else if (requestPath.equals("/dumpToLog")) {

                logFilter.dumpToLog(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)), "java".equals(httpServletRequest.getParameter("type")));
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());

            } else if (requestPath.equals("/retrieve")) {

                Expectation[] expectations = logFilter.retrieve(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
                httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
                httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=utf-8");
                IOStreamUtils.writeToOutputStream(expectationSerializer.serialize(expectations).getBytes(), httpServletResponse);

            } else if (requestPath.equals("/verify")) {

                String result = logFilter.verify(verificationSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
                if (result.isEmpty()) {
                    httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                } else {
                    httpServletResponse.setStatus(HttpStatusCode.NOT_ACCEPTABLE_406.code());
                    httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=utf-8");
                    IOStreamUtils.writeToOutputStream(result.getBytes(), httpServletResponse);
                }

            } else if (requestPath.equals("/verifySequence")) {

                String result = logFilter.verify(verificationSequenceSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
                if (result.isEmpty()) {
                    httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                } else {
                    httpServletResponse.setStatus(HttpStatusCode.NOT_ACCEPTABLE_406.code());
                    httpServletResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=utf-8");
                    IOStreamUtils.writeToOutputStream(result.getBytes(), httpServletResponse);
                }

            } else if (requestPath.equals("/stop")) {

                httpServletResponse.setStatus(HttpStatusCode.NOT_IMPLEMENTED_501.code());

            } else {
                forwardRequest(httpServletRequest, httpServletResponse);
            }
        } catch (Exception e) {
            logger.error("Exception processing " + httpServletRequest, e);
            httpServletResponse.setStatus(HttpStatusCode.BAD_REQUEST_400.code());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        forwardRequest(request, response);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        forwardRequest(request, response);
    }

    @Override
    protected void doTrace(HttpServletRequest request, HttpServletResponse response) {
        forwardRequest(request, response);
    }

    private void forwardRequest(HttpServletRequest request, HttpServletResponse httpServletResponse) {
        HttpResponse httpResponse = sendRequest(filters.applyOnRequestFilters(httpServletToMockServerRequestMapper.mapHttpServletRequestToMockServerRequest(request)));
        mockServerToHttpServletResponseMapper.mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);
    }

    private HttpResponse sendRequest(HttpRequest httpRequest) {
        // if HttpRequest was set to null by a filter don't send request
        if (httpRequest != null) {
            String hostHeader = httpRequest.getFirstHeader("Host");
            if (!Strings.isNullOrEmpty(hostHeader)) {
                String[] hostHeaderParts = hostHeader.split(":");

                Integer port = (httpRequest.isSecure() ? 443 : 80); // default
                if (hostHeaderParts.length > 1) {
                    port = Integer.parseInt(hostHeaderParts[1]);  // non-default
                }
                HttpResponse httpResponse = filters.applyOnResponseFilters(httpRequest, httpClient.sendRequest(outboundRequest(hostHeaderParts[0], port, "", httpRequest)));
                if (httpResponse != null) {
                    return httpResponse;
                }
            } else {
                logger.error("Host header must be provided for requests being forwarded, the following request does not include the \"Host\" header:" + System.getProperty("line.separator") + httpRequest);
                throw new IllegalArgumentException("Host header must be provided for requests being forwarded");
            }
        }
        return notFoundResponse();
    }
}
