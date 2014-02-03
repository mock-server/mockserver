package org.mockserver.server;

import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mappers.HttpServletToMockServerRequestMapper;
import org.mockserver.mappers.MockServerToHttpServletResponseMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.proxy.filters.LogFilter;
import org.mockserver.streams.IOStreamUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author jamesdbloom
 */
public class MockServerServlet extends HttpServlet {
    private static final long serialVersionUID = 5058943788293770703L;
    private MockServer mockServer = new MockServer();
    private HttpServletToMockServerRequestMapper httpServletToMockServerRequestMapper = new HttpServletToMockServerRequestMapper();
    private MockServerToHttpServletResponseMapper mockServerToHttpServletResponseMapper = new MockServerToHttpServletResponseMapper();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private LogFilter logFilter = new LogFilter();

    public void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    public void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockResponse(httpServletRequest, httpServletResponse);
    }

    public void doPut(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        String requestPath = httpServletRequest.getPathInfo() != null && httpServletRequest.getContextPath() != null ? httpServletRequest.getPathInfo() : httpServletRequest.getRequestURI();
        if (requestPath.equals("/stop")) {
            httpServletResponse.setStatus(HttpStatusCode.NOT_IMPLEMENTED_501.code());
        } else if (requestPath.equals("/dumpToLog")) {
            mockServer.dumpToLog(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
            httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
        } else if (requestPath.equals("/reset")) {
            logFilter.reset();
            mockServer.reset();
            httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
        } else if (requestPath.equals("/clear")) {
            HttpRequest httpRequest = httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest));
            logFilter.clear(httpRequest);
            mockServer.clear(httpRequest);
            httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
        } else if (requestPath.equals("/expectation")) {
            Expectation expectation = expectationSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest));
            mockServer.when(expectation.getHttpRequest(), expectation.getTimes()).thenRespond(expectation.getHttpResponse());
            httpServletResponse.setStatus(HttpStatusCode.CREATED_201.code());
        } else if (requestPath.equals("/retrieve")) {
            Expectation[] expectations = logFilter.retrieve(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToString(httpServletRequest)));
            IOStreamUtils.writeToOutputStream(expectationSerializer.serialize(expectations).getBytes(), httpServletResponse);
            httpServletResponse.setStatus(HttpStatusCode.OK_200.code());
        } else {
            mockResponse(httpServletRequest, httpServletResponse);
        }
    }

    private void mockResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        HttpRequest httpRequest = httpServletToMockServerRequestMapper.mapHttpServletRequestToMockServerRequest(httpServletRequest);
        HttpResponse httpResponse = mockServer.handle(httpRequest);
        logFilter.onResponse(httpRequest, httpResponse);
        if (httpResponse != null) {
            mockServerToHttpServletResponseMapper.mapMockServerResponseToHttpServletResponse(httpResponse, httpServletResponse);
        } else {
            httpServletResponse.setStatus(HttpStatusCode.NOT_FOUND_404.code());
        }
    }
}
