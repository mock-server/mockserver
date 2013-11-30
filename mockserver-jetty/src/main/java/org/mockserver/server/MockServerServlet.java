package org.mockserver.server;

import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.mappers.HttpServletRequestMapper;
import org.mockserver.mappers.HttpServletResponseMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class MockServerServlet extends HttpServlet {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private MockServer mockServer = new MockServer();
    private HttpServletRequestMapper httpServletRequestMapper = new HttpServletRequestMapper();
    private HttpServletResponseMapper httpServletResponseMapper = new HttpServletResponseMapper();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();

    public void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        doPOSTandGET(httpServletRequest, httpServletResponse);
    }

    public void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        doPOSTandGET(httpServletRequest, httpServletResponse);
    }

    private void doPOSTandGET(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        HttpRequest httpRequest = httpServletRequestMapper.createHttpRequest(httpServletRequest);
        HttpResponse httpResponse = mockServer.handle(httpRequest);
        if (httpResponse != null) {
            httpServletResponseMapper.mapHttpServletResponse(httpResponse, httpServletResponse);
        } else {
            httpServletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    public void doPut(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
        Expectation expectation = expectationSerializer.deserialize(httpServletRequest.getInputStream());
        if (httpServletRequest.getRequestURI().equals("/clear")) {
            mockServer.clear(expectation.getHttpRequest());
            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
        } else {
            mockServer.when(expectation.getHttpRequest(), expectation.getTimes()).respond(expectation.getHttpResponse());
            httpServletResponse.setStatus(HttpServletResponse.SC_CREATED);
        }

    }

}
