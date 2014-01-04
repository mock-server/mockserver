package org.mockserver.server;

import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mappers.HttpServletRequestMapper;
import org.mockserver.mappers.HttpServletResponseMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
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
    private HttpServletRequestMapper httpServletRequestMapper = new HttpServletRequestMapper();
    private HttpServletResponseMapper httpServletResponseMapper = new HttpServletResponseMapper();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();

    public void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        handlePOSTorGET(httpServletRequest, httpServletResponse);
    }

    public void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        handlePOSTorGET(httpServletRequest, httpServletResponse);
    }

    private void handlePOSTorGET(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        HttpRequest httpRequest = httpServletRequestMapper.mapHttpServletRequestToHttpRequest(httpServletRequest);
        HttpResponse httpResponse = mockServer.handle(httpRequest);
        System.out.println("httpRequest = " + httpRequest);
        if (httpResponse != null) {
            System.out.println("httpResponse = " + httpResponse);
            httpServletResponseMapper.mapHttpResponseToHttpServletResponse(httpResponse, httpServletResponse);
        } else {
            httpServletResponse.setStatus(HttpStatusCode.NOT_FOUND_404.code());
        }
    }

    public void doPut(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        switch (httpServletRequest.getPathInfo() != null && httpServletRequest.getContextPath() != null ? httpServletRequest.getPathInfo() : httpServletRequest.getRequestURI()) {
            case "/dumpToLog":
                mockServer.dumpToLog(null);
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                break;
            case "/reset":
                mockServer.reset();
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                break;
            case "/clear":
                mockServer.clear(httpRequestSerializer.deserialize(IOStreamUtils.readInputStreamToByteArray(httpServletRequest)));
                httpServletResponse.setStatus(HttpStatusCode.ACCEPTED_202.code());
                break;
            default:
                Expectation expectation = expectationSerializer.deserialize(IOStreamUtils.readInputStreamToByteArray(httpServletRequest));
                mockServer.when(expectation.getHttpRequest(), expectation.getTimes()).thenRespond(expectation.getHttpResponse());
                httpServletResponse.setStatus(HttpStatusCode.CREATED_201.code());
        }
    }

}
