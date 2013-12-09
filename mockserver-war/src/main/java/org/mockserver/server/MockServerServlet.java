package org.mockserver.server;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharEncoding;
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
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * @author jamesdbloom
 */
public class MockServerServlet extends HttpServlet {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private MockServer mockServer = new MockServer();
    private HttpServletRequestMapper httpServletRequestMapper = new HttpServletRequestMapper();
    private HttpServletResponseMapper httpServletResponseMapper = new HttpServletResponseMapper();
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();

    public void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
        handlePOSTorGET(httpServletRequest, httpServletResponse);
    }

    public void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
        handlePOSTorGET(httpServletRequest, httpServletResponse);
    }

    private void handlePOSTorGET(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
        HttpRequest httpRequest = httpServletRequestMapper.createHttpRequest(httpServletRequest);
        HttpResponse httpResponse = mockServer.handle(httpRequest);
        if (httpResponse != null) {
            httpServletResponseMapper.mapHttpServletResponse(httpResponse, httpServletResponse);
        } else {
            httpServletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    public void doPut(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
        if (httpServletRequest.getRequestURI().equals("/reset")) {
            mockServer.reset();
            httpServletResponse.setStatus(HttpServletResponse.SC_ACCEPTED);
        } else {
            byte[] jsonExpectation = IOUtils.toByteArray(new InputStreamReader(httpServletRequest.getInputStream()), Charset.forName(CharEncoding.UTF_8));
            Expectation expectation = expectationSerializer.deserialize(jsonExpectation);
            if (httpServletRequest.getRequestURI().equals("/clear")) {
                mockServer.clear(expectation.getHttpRequest());
                httpServletResponse.setStatus(HttpServletResponse.SC_ACCEPTED);
            } else {
                mockServer.when(expectation.getHttpRequest(), expectation.getTimes()).respond(expectation.getHttpResponse());
                httpServletResponse.setStatus(HttpServletResponse.SC_CREATED);
            }
        }
    }

}
