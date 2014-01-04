package org.mockserver.client.server;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.http.HttpRequestClient;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerClientTest {

    @Mock
    private MatcherBuilder matcherBuilder;
    @Mock
    private HttpRequestClient mockHttpClient;
    @InjectMocks
    private MockServerClient mockServerClient;

    @Before
    public void setupTestFixture() throws Exception {
        mockServerClient = new MockServerClient("localhost", 8080);

        initMocks(this);
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
        mockServerClient
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
        verify(mockHttpClient).sendPUTRequest("http://localhost:8080", "{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"path\" : \"/some_path\",\n" +
                "    \"body\" : \"some_request_body\"\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"statusCode\" : 200,\n" +
                "    \"body\" : \"some_response_body\",\n" +
                "    \"headers\" : [ {\n" +
                "      \"name\" : \"responseName\",\n" +
                "      \"values\" : [ \"responseValue\" ]\n" +
                "    } ]\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 0,\n" +
                "    \"unlimited\" : true\n" +
                "  }\n" +
                "}", "/");
    }

    @Test
    public void shouldSendResetRequest() throws Exception {
        // when
        mockServerClient.reset();

        // then
        verify(mockHttpClient).sendPUTRequest("http://localhost:8080", "", "/reset");
    }

    @Test
    public void shouldSendDumpToLogRequest() throws Exception {
        // when
        mockServerClient.dumpToLog();

        // then
        verify(mockHttpClient).sendPUTRequest("http://localhost:8080", "", "/dumpToLog");
    }

    @Test
    public void shouldSendClearRequest() throws Exception {
        // when
        mockServerClient
                .clear(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withBody("some_request_body")
                );

        // then
        verify(mockHttpClient).sendPUTRequest("http://localhost:8080", "{\n" +
                "  \"path\" : \"/some_path\",\n" +
                "  \"body\" : \"some_request_body\"\n" +
                "}", "/clear");
    }
}
