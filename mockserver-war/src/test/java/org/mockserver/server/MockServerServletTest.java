package org.mockserver.server;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mappers.HttpServletRequestMapper;
import org.mockserver.mappers.HttpServletResponseMapper;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServer;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerServletTest {

    @Mock
    private MockServer mockServer;
    @Mock
    private HttpServletRequestMapper httpServletRequestMapper;
    @Mock
    private HttpServletResponseMapper httpServletResponseMapper;
    @Mock
    private ExpectationSerializer expectationSerializer;
    @Mock
    private HttpRequestSerializer httpRequestSerializer;
    @InjectMocks
    private MockServerServlet mockServerServlet;

    @Before
    public void setupTestFixture() {
        mockServerServlet = new MockServerServlet();

        initMocks(this);
    }

    @Test
    public void respondWhenPathMatches() throws IOException {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("somepath");
        HttpResponse httpResponse = new HttpResponse().withHeaders(new Header("name", "value")).withBody("somebody");
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "somepath");

        when(mockServer.handle(httpRequest)).thenReturn(httpResponse);
        when(httpServletRequestMapper.mapHttpServletRequestToHttpRequest(httpServletRequest)).thenReturn(httpRequest);

        // when
        mockServerServlet.doGet(httpServletRequest, httpServletResponse);

        // then
        verify(httpServletResponseMapper).mapHttpResponseToHttpServletResponse(httpResponse, httpServletResponse);
    }

    @Test
    public void setupExpectation() throws IOException {
        // given
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest httpRequest = mock(HttpRequest.class);
        Times times = mock(Times.class);
        Expectation expectation = new Expectation(httpRequest, times).thenRespond(new HttpResponse());

        byte[] requestBytes = "requestBytes".getBytes();
        httpServletRequest.setContent(requestBytes);
        when(expectationSerializer.deserialize(requestBytes)).thenReturn(expectation);
        when(mockServer.when(same(httpRequest), same(times))).thenReturn(expectation);

        // when
        mockServerServlet.doPut(httpServletRequest, httpServletResponse);

        // then
        verify(mockServer).when(same(httpRequest), same(times));
        assertEquals(HttpServletResponse.SC_CREATED, httpServletResponse.getStatus());
    }

    @Test
    public void setupExpectationFromJSONWithAllDefault() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        String jsonExpectation = "{" +
                "    \"httpRequest\": {" +
                "        \"method\": \"\", " +
                "        \"path\": \"\", " +
                "        \"body\": \"\", " +
                "        \"headers\": [ ], " +
                "        \"cookies\": [ ] " +
                "    }, " +
                "    \"httpResponse\": {" +
                "        \"statusCode\": 200, " +
                "        \"body\": \"\", " +
                "        \"cookies\": [ ], " +
                "        \"headers\": [ ], " +
                "        \"delay\": {" +
                "            \"timeUnit\": \"MICROSECONDS\", " +
                "            \"value\": 0" +
                "        }" +
                "    }, " +
                "    \"times\": {" +
                "        \"remainingTimes\": 1, " +
                "        \"unlimited\": true" +
                "    }" +
                "}";
        httpServletRequest.setContent(jsonExpectation.getBytes());

        // when
        new MockServerServlet().doPut(httpServletRequest, httpServletResponse);

        // then
        assertEquals(httpServletResponse.getStatus(), HttpServletResponse.SC_CREATED);
    }

    @Test
    public void setupExpectationFromJSONWithAllEmpty() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        String jsonExpectation = "{" +
                "    \"httpRequest\": { }," +
                "    \"httpResponse\": { }," +
                "    \"times\": { }" +
                "}";
        httpServletRequest.setContent(jsonExpectation.getBytes());

        // when
        new MockServerServlet().doPut(httpServletRequest, httpServletResponse);

        // then
        assertEquals(httpServletResponse.getStatus(), HttpServletResponse.SC_CREATED);
    }

    @Test
    public void setupExpectationFromJSONWithPartiallyEmptyFields() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        String jsonExpectation = "{" +
                "    \"httpRequest\": {" +
                "        \"path\": \"\"" +
                "    }, " +
                "    \"httpResponse\": {" +
                "        \"body\": \"\"" +
                "    }, " +
                "    \"times\": {" +
                "        \"remainingTimes\": 1, " +
                "        \"unlimited\": true" +
                "    }" +
                "}";
        httpServletRequest.setContent(jsonExpectation.getBytes());

        // when
        new MockServerServlet().doPut(httpServletRequest, httpServletResponse);

        // then
        assertEquals(httpServletResponse.getStatus(), HttpServletResponse.SC_CREATED);
    }

    @Test
    public void shouldClearExpectations() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/clear");
        HttpRequest httpRequest = new HttpRequest();

        byte[] requestBytes = "requestBytes".getBytes();
        httpServletRequest.setContent(requestBytes);
        when(httpRequestSerializer.deserialize(requestBytes)).thenReturn(httpRequest);

        // when
        mockServerServlet.doPut(httpServletRequest, httpServletResponse);

        // then
        verify(mockServer).clear(httpRequest);
        verifyNoMoreInteractions(httpServletRequestMapper);
    }

    @Test
    public void shouldResetMockServer() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/reset");
        Expectation expectation = new Expectation(new HttpRequest(), Times.unlimited()).thenRespond(new HttpResponse());

        byte[] requestBytes = "requestBytes".getBytes();
        httpServletRequest.setContent(requestBytes);
        when(expectationSerializer.deserialize(requestBytes)).thenReturn(expectation);

        // when
        mockServerServlet.doPut(httpServletRequest, httpServletResponse);

        // then
        verify(mockServer).reset();
        verifyNoMoreInteractions(httpServletRequestMapper);
    }

    @Test
    public void shouldDumpAllExpectationsToLog() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/dumpToLog");
        HttpRequest httpRequest = new HttpRequest();

        byte[] requestBytes = "requestBytes".getBytes();
        httpServletRequest.setContent(requestBytes);
        when(httpRequestSerializer.deserialize(requestBytes)).thenReturn(httpRequest);

        // when
        mockServerServlet.doPut(httpServletRequest, httpServletResponse);

        // then
        verify(httpRequestSerializer).deserialize(requestBytes);
        verify(mockServer).dumpToLog(httpRequest);
        verifyNoMoreInteractions(httpServletRequestMapper);
    }
}
