package org.mockserver.server;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mappers.HttpServletToMockServerRequestMapper;
import org.mockserver.mappers.MockServerToHttpServletResponseMapper;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.HttpCallbackActionHandler;
import org.mockserver.mock.action.HttpForwardActionHandler;
import org.mockserver.mock.action.HttpResponseActionHandler;
import org.mockserver.model.*;
import org.mockserver.proxy.filters.Filters;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerServletTest {

    @Mock
    private MockServerMatcher mockServerMatcher;
    @Mock
    private Filters filters;
    @Mock
    private ApacheHttpClient apacheHttpClient;
    @Mock
    private HttpServletToMockServerRequestMapper httpServletToMockServerRequestMapper;
    @Mock
    private MockServerToHttpServletResponseMapper mockServerToHttpServletResponseMapper;
    @Mock
    private ExpectationSerializer expectationSerializer;
    @Mock
    private HttpRequestSerializer httpRequestSerializer;
    @Mock
    private HttpResponseActionHandler httpResponseActionHandler;
    @Mock
    private HttpForwardActionHandler httpForwardActionHandler;
    @Mock
    private HttpCallbackActionHandler httpCallbackActionHandler;
    @InjectMocks
    private MockServerServlet mockServerServlet;

    @Before
    public void setupTestFixture() {
        mockServerServlet = new MockServerServlet();

        initMocks(this);
    }

    @Test
    public void shouldReturnMatchedExpectation() {
        // given
        HttpRequest request = new HttpRequest().withPath("somepath");
        HttpResponse response = new HttpResponse().withHeaders(new Header("name", "value")).withBody("somebody");
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "somepath");

        when(httpServletToMockServerRequestMapper.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);
        when(mockServerMatcher.handle(any(HttpRequest.class))).thenReturn(response);
        when(httpResponseActionHandler.handle(any(HttpResponse.class), any(HttpRequest.class))).thenReturn(response);

        // when
        mockServerServlet.doGet(httpServletRequest, httpServletResponse);

        // then
        when(httpServletToMockServerRequestMapper.mapHttpServletRequestToMockServerRequest(httpServletRequest)).thenReturn(request);
        verify(mockServerMatcher).handle(request);
        when(httpResponseActionHandler.handle(response, request)).thenReturn(response);
        verify(mockServerToHttpServletResponseMapper).mapMockServerResponseToHttpServletResponse(response, httpServletResponse);
        assertThat(httpServletResponse.getStatus(), is(200));
    }

    @Test
    public void shouldForwardMatchedExpectation() throws IOException {
        // given
        HttpRequest request = new HttpRequest().withPath("somepath");
        HttpForward forward = new HttpForward().withHost("some-host").withPort(1234);
        HttpResponse response = new HttpResponse();
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "somepath");

        when(httpServletToMockServerRequestMapper.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);
        when(mockServerMatcher.handle(any(HttpRequest.class))).thenReturn(forward);
        when(httpForwardActionHandler.handle(any(HttpForward.class), any(HttpRequest.class))).thenReturn(response);

        // when
        mockServerServlet.doGet(httpServletRequest, httpServletResponse);

        // then
        when(httpServletToMockServerRequestMapper.mapHttpServletRequestToMockServerRequest(httpServletRequest)).thenReturn(request);
        verify(mockServerMatcher).handle(request);
        when(httpForwardActionHandler.handle(forward, request)).thenReturn(response);
        verify(mockServerToHttpServletResponseMapper).mapMockServerResponseToHttpServletResponse(response, httpServletResponse);
        assertThat(httpServletResponse.getStatus(), is(200));
    }

    @Test
    public void shouldCallbackMatchedExpectation() throws IOException {
        // given
        HttpRequest request = new HttpRequest().withPath("somepath");
        HttpCallback callback = new HttpCallback().withCallbackClass("some-class");
        HttpResponse response = new HttpResponse();
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "somepath");

        when(httpServletToMockServerRequestMapper.mapHttpServletRequestToMockServerRequest(any(HttpServletRequest.class))).thenReturn(request);
        when(mockServerMatcher.handle(any(HttpRequest.class))).thenReturn(callback);
        when(httpCallbackActionHandler.handle(any(HttpCallback.class), any(HttpRequest.class))).thenReturn(response);

        // when
        mockServerServlet.doGet(httpServletRequest, httpServletResponse);

        // then
        when(httpServletToMockServerRequestMapper.mapHttpServletRequestToMockServerRequest(httpServletRequest)).thenReturn(request);
        verify(mockServerMatcher).handle(request);
        when(httpCallbackActionHandler.handle(callback, request)).thenReturn(response);
        verify(mockServerToHttpServletResponseMapper).mapMockServerResponseToHttpServletResponse(response, httpServletResponse);
        assertThat(httpServletResponse.getStatus(), is(200));
    }

    @Test
    public void setupExpectation() throws IOException {
        // given
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/expectation");
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        HttpRequest httpRequest = mock(HttpRequest.class);
        Times times = mock(Times.class);
        Expectation expectation = new Expectation(httpRequest, times).thenRespond(new HttpResponse());

        String requestBytes = "requestBytes";
        httpServletRequest.setContent(requestBytes.getBytes());
        when(expectationSerializer.deserialize(requestBytes)).thenReturn(expectation);
        when(mockServerMatcher.when(same(httpRequest), same(times))).thenReturn(expectation);

        // when
        mockServerServlet.doPut(httpServletRequest, httpServletResponse);

        // then
        verify(mockServerMatcher).when(same(httpRequest), same(times));
        assertEquals(HttpServletResponse.SC_CREATED, httpServletResponse.getStatus());
    }

    @Test
    public void setupExpectationFromJSONWithAllDefault() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/expectation");
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
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/expectation");
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
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/expectation");
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

        String requestBytes = "requestBytes";
        httpServletRequest.setContent(requestBytes.getBytes());
        when(httpRequestSerializer.deserialize(requestBytes)).thenReturn(httpRequest);

        // when
        mockServerServlet.doPut(httpServletRequest, httpServletResponse);

        // then
        verify(mockServerMatcher).clear(httpRequest);
        verifyNoMoreInteractions(httpServletToMockServerRequestMapper);
    }

    @Test
    public void shouldResetMockServer() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/reset");
        Expectation expectation = new Expectation(new HttpRequest(), Times.unlimited()).thenRespond(new HttpResponse());

        String requestBytes = "requestBytes";
        httpServletRequest.setContent(requestBytes.getBytes());
        when(expectationSerializer.deserialize(requestBytes)).thenReturn(expectation);

        // when
        mockServerServlet.doPut(httpServletRequest, httpServletResponse);

        // then
        verify(mockServerMatcher).reset();
        verifyNoMoreInteractions(httpServletToMockServerRequestMapper);
    }

    @Test
    public void shouldDumpAllExpectationsToLog() throws IOException {
        // given
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("PUT", "/dumpToLog");
        HttpRequest httpRequest = new HttpRequest();

        String requestBytes = "requestBytes";
        httpServletRequest.setContent(requestBytes.getBytes());
        when(httpRequestSerializer.deserialize(requestBytes)).thenReturn(httpRequest);

        // when
        mockServerServlet.doPut(httpServletRequest, httpServletResponse);

        // then
        verify(httpRequestSerializer).deserialize(requestBytes);
        verify(mockServerMatcher).dumpToLog(httpRequest);
        verifyNoMoreInteractions(httpServletToMockServerRequestMapper);
    }
}
