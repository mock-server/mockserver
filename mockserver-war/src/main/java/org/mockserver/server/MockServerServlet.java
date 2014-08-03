package org.mockserver.server;

import org.apache.http.client.utils.URIBuilder;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mappers.HttpServletToMockServerRequestMapper;
import org.mockserver.mappers.MockServerToHttpServletResponseMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.model.*;
import org.mockserver.proxy.filters.Filters;
import org.mockserver.proxy.filters.HopByHopHeaderFilter;
import org.mockserver.proxy.filters.LogFilter;
import org.mockserver.streams.IOStreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URISyntaxException;

/**
 * @author jamesdbloom
 */
public class MockServerServlet extends HttpServlet {

    private static final long serialVersionUID = 5058943788293770703L;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private MockServerMatcher mockServerMatcher = new MockServerMatcher();
    private HttpServletToMockServerRequestMapper httpServletToMockServerRequestMapper = new HttpServletToMockServerRequestMapper();
    private MockServerToHttpServletResponseMapper mockServerToHttpServletResponseMapper = new MockServerToHttpServletResponseMapper();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private ApacheHttpClient apacheHttpClient = new ApacheHttpClient(true);
    private LogFilter logFilter = new LogFilter();
    private Filters filters = new Filters();

    public MockServerServlet() {
        filters.withFilter(new HttpRequest(), new HopByHopHeaderFilter());
        filters.withFilter(new HttpRequest(), logFilter);
    }

    public void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    public void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    public void doPut(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        String requestPath = retrieveRequestPath(httpServletRequest);
        if (requestPath.equals("/stop")) {
            httpServletResponse.setStatus(HttpStatusCode.NOT_IMPLEMENTED_501.code());
        } else if (requestPath.equals("/dumpToLog")) {
            mockServerMatcher.dumpToLog(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
            httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
        } else if (requestPath.equals("/reset")) {
            logFilter.reset();
            mockServerMatcher.reset();
            httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
        } else if (requestPath.equals("/clear")) {
            HttpRequest httpRequest = httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest));
            logFilter.clear(httpRequest);
            mockServerMatcher.clear(httpRequest);
            httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
        } else if (requestPath.equals("/expectation")) {
            Expectation expectation = expectationSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest));
            mockServerMatcher.when(expectation.getHttpRequest(), expectation.getTimes()).thenRespond(expectation.getHttpResponse(false)).thenForward(expectation.getHttpForward());
            httpServletResponse.setStatus(HttpStatusCode.CREATED_201.code());
        } else if (requestPath.equals("/retrieve")) {
            Expectation[] expectations = logFilter.retrieve(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
            IOStreamUtils.writeToOutputStream(expectationSerializer.serialize(expectations).getBytes(), httpServletResponse);
            httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
        } else {
            mockResponse(httpServletRequest, httpServletResponse);
        }
    }

    private String retrieveRequestPath(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getPathInfo() != null && httpServletRequest.getContextPath() != null ? httpServletRequest.getPathInfo() : httpServletRequest.getRequestURI();
    }

    private void mockResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        HttpRequest httpRequest = httpServletToMockServerRequestMapper.mapHttpServletRequestToMockServerRequest(httpServletRequest);
        Action action = mockServerMatcher.handle(httpRequest);
        if (action instanceof HttpForward) {
            HttpForward httpForward = (HttpForward) action;
            httpServletRequest.getRequestURL();
            forwardRequest(httpServletRequest, httpServletResponse, httpForward);
        } else {
            HttpResponse httpResponse = (HttpResponse) action;
            logFilter.onResponse(httpRequest, httpResponse);
            if (httpResponse != null) {
                mockServerToHttpServletResponseMapper.mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);
            } else {
                httpServletResponse.setStatus(HttpStatusCode.NOT_FOUND_404.code());
            }
        }
    }

    private HttpRequest updateUrl(HttpRequest httpRequest, HttpServletRequest httpServletRequest, HttpForward httpForward) {
        try {
            URIBuilder uriBuilder = new URIBuilder(httpServletRequest.getRequestURL().toString());
            uriBuilder.setPath(retrieveRequestPath(httpServletRequest));
            uriBuilder.setHost(httpForward.getHost());
            uriBuilder.setPort(httpForward.getPort());
            uriBuilder.setScheme(httpForward.getScheme().name().toLowerCase());
            uriBuilder.setCustomQuery(httpServletRequest.getQueryString());
            httpRequest.withURL(uriBuilder.toString());
        } catch (URISyntaxException e) {
            logger.warn("URISyntaxException for url " + httpServletRequest.getRequestURL(), e);
        }
        return httpRequest;
    }

    private void forwardRequest(HttpServletRequest request, HttpServletResponse response, HttpForward httpForward) {
        HttpRequest httpRequest = updateUrl(httpServletToMockServerRequestMapper.mapHttpServletRequestToMockServerRequest(request), request, httpForward);
        sendRequest(filters.applyFilters(httpRequest), response);
    }

    private void sendRequest(final HttpRequest httpRequest, final HttpServletResponse httpServletResponse) {
        // if HttpRequest was set to null by a filter don't send request
        if (httpRequest != null) {
            HttpResponse httpResponse = filters.applyFilters(httpRequest, apacheHttpClient.sendRequest(httpRequest, false));
            mockServerToHttpServletResponseMapper.mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);
        }
    }
}
