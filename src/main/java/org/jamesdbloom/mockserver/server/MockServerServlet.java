package org.jamesdbloom.mockserver.server;

import org.jamesdbloom.mockserver.mappers.ExpectationMapper;
import org.jamesdbloom.mockserver.mappers.HttpServletRequestMapper;
import org.jamesdbloom.mockserver.mappers.HttpServletResponseMapper;
import org.jamesdbloom.mockserver.mock.MockServer;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.HttpResponse;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author jamesdbloom
 */
public class MockServerServlet extends HttpServlet {

    private MockServer mockServer = new MockServer();
    private HttpServletRequestMapper httpServletRequestMapper = new HttpServletRequestMapper();
    private HttpServletResponseMapper httpServletResponseMapper = new HttpServletResponseMapper();
    private ExpectationMapper expectationMapper = new ExpectationMapper();

    public void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        HttpRequest httpRequest = httpServletRequestMapper.createHttpRequest(httpServletRequest);
        HttpResponse httpResponse = mockServer.handle(httpRequest);
        if (httpResponse != null) {
            httpServletResponseMapper.mapHttpServletResponse(httpResponse, httpServletResponse);
        } else {
            httpServletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    public void doPut(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        mockServer.addExpectation(expectationMapper.deserialize(httpServletRequest));
        httpServletResponse.setStatus(HttpServletResponse.SC_CREATED);
    }

}
