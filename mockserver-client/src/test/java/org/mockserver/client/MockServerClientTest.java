package org.mockserver.client;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockserver.mappers.ExpectationMapper;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerClientTest {

    @Mock
    private ExpectationMapper expectationMapper;
    @InjectMocks
    private MockServerClient mockServerClient;

    @Spy
    private HttpClient mockHttpClient = new HttpClient();
    @Mock
    private Request mockRequest = mock(Request.class);

    @Before
    public void setupTestFixture() throws Exception {
        mockServerClient = spy(new MockServerClient("localhost", 8080));
        // - do not send expectation
        doNothing().when(mockServerClient).sendExpectation(any(Expectation.class));

        initMocks(this);

        // - do nothing when start is called
        doNothing().when(mockHttpClient).start();
        // - an http client that can create a request
        when(mockHttpClient.newRequest(anyString())).thenReturn(mockRequest);
        // - a request that has a fluent API
        when(mockRequest.method(any(HttpMethod.class))).thenReturn(mockRequest);
        when(mockRequest.header(anyString(), anyString())).thenReturn(mockRequest);
        when(mockRequest.content(any(StringContentProvider.class))).thenReturn(mockRequest);
    }

    @Test
    public void shouldSetupExpectation() {
        // given
        HttpRequest httpRequest =
                new HttpRequest()
                        .withPath("/some_path")
                        .withBody("some_request_body");
        HttpResponse httpResponse =
                new HttpResponse()
                        .withBody("some_response_body")
                        .withHeaders(new Header("responseName", "responseValue"));

        // when
        ForwardChainExpectation forwardChainExpectation = mockServerClient.when(httpRequest);
        forwardChainExpectation.respond(httpResponse);

        // then
        Expectation expectation = forwardChainExpectation.getExpectation();
        assertTrue(expectation.matches(httpRequest));
        assertSame(httpResponse, expectation.getHttpResponse());
        assertEquals(Times.unlimited(), expectation.getTimes());
    }

    @Test
    public void shouldSendExpectationRequest() throws Exception {
        // when
        new MockServerClient("localhost", 8080, mockHttpClient)
                .when(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody("some_request_body")
                )
                .respond(
                        new HttpResponse()
                                .withBody("some_response_body")
                                .withHeaders(new Header("responseName", "responseValue")
                                )
                );

        // then
        verify(mockHttpClient).newRequest("http://localhost:8080/");
        verify(mockRequest).method(HttpMethod.PUT);
        verify(mockRequest).header("Content-Type", "application/json; charset=utf-8");
        verify(mockRequest).content(new MockServerClient.ComparableStringContentProvider("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"method\" : \"\",\n" +
                "    \"path\" : \"/some_path\",\n" +
                "    \"body\" : \"some_request_body\",\n" +
                "    \"cookies\" : [ ],\n" +
                "    \"headers\" : [ ],\n" +
                "    \"parameters\" : [ ]\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"statusCode\" : 200,\n" +
                "    \"body\" : \"some_response_body\",\n" +
                "    \"cookies\" : [ ],\n" +
                "    \"headers\" : [ {\n" +
                "      \"name\" : \"responseName\",\n" +
                "      \"values\" : [ \"responseValue\" ]\n" +
                "    } ],\n" +
                "    \"delay\" : {\n" +
                "      \"timeUnit\" : \"MICROSECONDS\",\n" +
                "      \"value\" : 0\n" +
                "    }\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 1,\n" +
                "    \"unlimited\" : true\n" +
                "  }\n" +
                "}", "UTF-8"));
        verify(mockRequest).send();
    }

    @Test
    public void shouldSendClearRequest() throws Exception {
        // when
        new MockServerClient("localhost", 8080, mockHttpClient)
                .clear(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody("some_request_body")
                );

        // then
        verify(mockHttpClient).newRequest("http://localhost:8080/clear");
        verify(mockRequest).method(HttpMethod.PUT);
        verify(mockRequest).header("Content-Type", "application/json; charset=utf-8");
        verify(mockRequest).content(new MockServerClient.ComparableStringContentProvider("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"method\" : \"\",\n" +
                "    \"path\" : \"/some_path\",\n" +
                "    \"body\" : \"some_request_body\",\n" +
                "    \"cookies\" : [ ],\n" +
                "    \"headers\" : [ ],\n" +
                "    \"parameters\" : [ ]\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"statusCode\" : 200,\n" +
                "    \"body\" : \"\",\n" +
                "    \"cookies\" : [ ],\n" +
                "    \"headers\" : [ ],\n" +
                "    \"delay\" : {\n" +
                "      \"timeUnit\" : \"MICROSECONDS\",\n" +
                "      \"value\" : 0\n" +
                "    }\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 1,\n" +
                "    \"unlimited\" : true\n" +
                "  }\n" +
                "}", "UTF-8"));
        verify(mockRequest).send();
    }

    @Test
    public void shouldSendResetRequest() throws Exception {
        // when
        new MockServerClient("localhost", 8080, mockHttpClient).reset();

        // then
        verify(mockHttpClient).newRequest("http://localhost:8080/reset");
        verify(mockRequest).method(HttpMethod.PUT);
        verify(mockRequest).header("Content-Type", "application/json; charset=utf-8");
        verify(mockRequest).content(new MockServerClient.ComparableStringContentProvider("", "UTF-8"));
        verify(mockRequest).send();
    }

    @Test
    public void shouldSendDumpToLogRequest() throws Exception {
        // when
        new MockServerClient("localhost", 8080, mockHttpClient).dumpToLog();

        // then
        verify(mockHttpClient).newRequest("http://localhost:8080/dumpToLog");
        verify(mockRequest).method(HttpMethod.PUT);
        verify(mockRequest).header("Content-Type", "application/json; charset=utf-8");
        verify(mockRequest).content(new MockServerClient.ComparableStringContentProvider("", "UTF-8"));
        verify(mockRequest).send();
    }
}
