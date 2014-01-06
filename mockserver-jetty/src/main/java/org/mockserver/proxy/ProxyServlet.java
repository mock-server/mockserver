package org.mockserver.proxy;

import org.mockserver.client.http.HttpRequestClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mappers.HttpServletRequestMapper;
import org.mockserver.mappers.HttpServletResponseMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.proxy.filters.*;
import org.mockserver.streams.IOStreamUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class ProxyServlet extends HttpServlet {
    private static final long serialVersionUID = 8490389904399790169L;
    private HttpServletRequestMapper httpServletRequestMapper = new HttpServletRequestMapper();
    private HttpServletResponseMapper httpServletResponseMapper = new HttpServletResponseMapper();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestClient httpRequestClient = new HttpRequestClient();
    private LogFilter logFilter = new LogFilter();
    private Filters filters = new Filters();

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
    public ProxyServlet withFilter(HttpRequest httpRequest, ProxyRequestFilter filter) {
        filters.withFilter(httpRequest, filter);
        return this;
    }

    /**
     * Add filter for HTTP response, each filter get called after each request has been proxied
     *
     * @param httpRequest the request to match against for this filter
     * @param filter the filter that is executed after this request has been proxied
     */
    public ProxyServlet withFilter(HttpRequest httpRequest, ProxyResponseFilter filter) {
        filters.withFilter(httpRequest, filter);
        return this;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        forwardRequest(request, response);
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        forwardRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        forwardRequest(request, response);
    }

    @Override
    protected void doPut(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        switch (httpServletRequest.getPathInfo() != null && httpServletRequest.getContextPath() != null ? httpServletRequest.getPathInfo() : httpServletRequest.getRequestURI()) {
            case "/dumpToLog":
                logFilter.dumpToLog(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToByteArray(httpServletRequest)), "java".equals(httpServletRequest.getParameter("type")));
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                break;
            case "/retrieve":
                Expectation[] expectations = logFilter.retrieve(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToByteArray(httpServletRequest)));
                IOStreamUtils.writeToOutputStream(expectationSerializer.serialize(expectations).getBytes(), httpServletResponse);
                httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
                break;
            case "/reset":
                logFilter.reset();
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                break;
            case "/clear":
                logFilter.clear(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToByteArray(httpServletRequest)));
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                break;
            default:
                forwardRequest(httpServletRequest, httpServletResponse);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        forwardRequest(request, response);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        forwardRequest(request, response);
    }

    @Override
    protected void doTrace(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        forwardRequest(request, response);
    }

    private void forwardRequest(HttpServletRequest request, HttpServletResponse response) {
        sendRequest(filters.applyFilters(httpServletRequestMapper.mapHttpServletRequestToHttpRequest(request)), response);
    }

    private void sendRequest(final HttpRequest httpRequest, final HttpServletResponse httpServletResponse) {
        // if HttpRequest was set to null by a filter don't send request
        if (httpRequest != null) {
            HttpResponse httpResponse = filters.applyFilters(httpRequest, httpRequestClient.sendRequest(httpRequest));
            httpServletResponseMapper.mapHttpResponseToHttpServletResponse(httpResponse, httpServletResponse);
        }
    }
}
