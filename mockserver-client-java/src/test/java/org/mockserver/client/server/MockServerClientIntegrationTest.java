package org.mockserver.client.server;

import org.junit.*;
import org.junit.rules.ExpectedException;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.java.HttpRequestToJavaSerializer;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.socket.PortFactory;
import org.mockserver.verify.VerificationTimes;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.mockserver.client.server.MockServerClient.TYPE.LOG;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.unlimited;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpForward.Scheme.HTTPS;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationSequence.verificationSequence;

/**
 * @author jamesdbloom
 */
public class MockServerClientIntegrationTest {

    private static MockServerClient mockServerClient;
    private static EchoServer echoServer;
    private static RequestLogFilter requestLogFilter;
    private static int freePort;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @BeforeClass
    public static void startEchoServer() {
        freePort = PortFactory.findFreePort();
        echoServer = new EchoServer(freePort, false);
        requestLogFilter = echoServer.requestLogFilter();
        mockServerClient = new MockServerClient("localhost", freePort);
    }

    @AfterClass
    public static void stopEchoServer() {
        echoServer.stop();
    }

    @Before
    public void clearRequestLog() {
        requestLogFilter.reset();
    }

    @Test
    public void shouldSetupExpectationWithResponse() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
                .when(
                        request()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body"))
                )
                .respond(
                        response()
                                .withBody("some_response_body")
                                .withHeaders(new Header("responseName", "responseValue"))
                );

        // then
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/expectation")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
                        .withBody(new StringBody("" +
                                "{\n" +
                                "  \"httpRequest\" : {\n" +
                                "    \"path\" : \"/some_path\",\n" +
                                "    \"body\" : \"some_request_body\"\n" +
                                "  },\n" +
                                "  \"httpResponse\" : {\n" +
                                "    \"headers\" : [ {\n" +
                                "      \"name\" : \"responseName\",\n" +
                                "      \"values\" : [ \"responseValue\" ]\n" +
                                "    } ],\n" +
                                "    \"body\" : \"some_response_body\"\n" +
                                "  },\n" +
                                "  \"times\" : {\n" +
                                "    \"remainingTimes\" : 0,\n" +
                                "    \"unlimited\" : true\n" +
                                "  },\n" +
                                "  \"timeToLive\" : {\n" +
                                "    \"unlimited\" : true\n" +
                                "  }\n" +
                                "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithForward() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
                .when(
                        request()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body"))
                )
                .forward(
                        forward()
                                .withHost("some_host")
                                .withPort(9090)
                                .withScheme(HTTPS)
                );

        // then
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/expectation")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
                        .withBody(new StringBody("" +
                                "{\n" +
                                "  \"httpRequest\" : {\n" +
                                "    \"path\" : \"/some_path\",\n" +
                                "    \"body\" : \"some_request_body\"\n" +
                                "  },\n" +
                                "  \"httpForward\" : {\n" +
                                "    \"host\" : \"some_host\",\n" +
                                "    \"port\" : 9090,\n" +
                                "    \"scheme\" : \"HTTPS\"\n" +
                                "  },\n" +
                                "  \"times\" : {\n" +
                                "    \"remainingTimes\" : 0,\n" +
                                "    \"unlimited\" : true\n" +
                                "  },\n" +
                                "  \"timeToLive\" : {\n" +
                                "    \"unlimited\" : true\n" +
                                "  }\n" +
                                "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithError() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
                .when(
                        request()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body"))
                )
                .error(
                        error()
                                .withDropConnection(true)
                                .withResponseBytes("silly_bytes".getBytes())
                );

        // then
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/expectation")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
                        .withBody(new StringBody("" +
                                "{\n" +
                                "  \"httpRequest\" : {\n" +
                                "    \"path\" : \"/some_path\",\n" +
                                "    \"body\" : \"some_request_body\"\n" +
                                "  },\n  \"httpError\" : {\n" +
                                "    \"dropConnection\" : true,\n" +
                                "    \"responseBytes\" : \"c2lsbHlfYnl0ZXM=\"\n" +
                                "  },\n  \"times\" : {\n" +
                                "    \"remainingTimes\" : 0,\n" +
                                "    \"unlimited\" : true\n" +
                                "  },\n" +
                                "  \"timeToLive\" : {\n" +
                                "    \"unlimited\" : true\n" +
                                "  }\n" +
                                "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSetupExpectationWithClassCallback() {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
                .when(
                        request()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body"))
                )
                .callback(
                        callback()
                                .withCallbackClass("some_class")
                );

        // then
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/expectation")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
                        .withBody(new StringBody("" +
                                "{\n" +
                                "  \"httpRequest\" : {\n" +
                                "    \"path\" : \"/some_path\",\n" +
                                "    \"body\" : \"some_request_body\"\n" +
                                "  },\n" +
                                "  \"httpClassCallback\" : {\n" +
                                "    \"callbackClass\" : \"some_class\"\n" +
                                "  },\n" +
                                "  \"times\" : {\n" +
                                "    \"remainingTimes\" : 0,\n" +
                                "    \"unlimited\" : true\n" +
                                "  },\n" +
                                "  \"timeToLive\" : {\n" +
                                "    \"unlimited\" : true\n" +
                                "  }\n" +
                                "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendExpectationRequestWithExactTimes() throws Exception {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
                .when(
                        request()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body")),
                        exactly(3)
                )
                .respond(
                        response()
                                .withBody("some_response_body")
                                .withHeaders(new Header("responseName", "responseValue"))
                );

        // then
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/expectation")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
                        .withBody(new StringBody("" +
                                "{\n" +
                                "  \"httpRequest\" : {\n" +
                                "    \"path\" : \"/some_path\",\n" +
                                "    \"body\" : \"some_request_body\"\n" +
                                "  },\n" +
                                "  \"httpResponse\" : {\n" +
                                "    \"headers\" : [ {\n" +
                                "      \"name\" : \"responseName\",\n" +
                                "" +
                                "      \"values\" : [ \"responseValue\" ]\n" +
                                "    } ],\n" +
                                "    \"body\" : \"some_response_body\"\n" +
                                "  },\n" +
                                "  \"times\" : {\n" +
                                "    \"remainingTimes\" : 3,\n" +
                                "    \"unlimited\" : false\n" +
                                "  },\n" +
                                "  \"timeToLive\" : {\n" +
                                "    \"unlimited\" : true\n" +
                                "  }\n" +
                                "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendDumpToLogRequest() throws Exception {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient.dumpToLog();

        // then
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/dumpToLog")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("content-length", "0"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendStopRequest() throws Exception {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient.stop();

        // then
        String result = requestLogFilter.verify(verificationSequence().withRequests(
                request()
                        .withMethod("PUT")
                        .withPath("/stop")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("content-length", "0"),
                                new Header("connection", "keep-alive")
                        )
                        .withSecure(false)
                        .withKeepAlive(true),
                request()
                        .withMethod("PUT")
                        .withPath("/status")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("content-length", "0"),
                                new Header("connection", "keep-alive")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldQueryRunningStatus() throws Exception {
        // given
        echoServer.withNextResponse(response().withStatusCode(200));

        // when
        boolean isRunning = mockServerClient.isRunning();

        // then
        assertThat(isRunning, is(true));
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/status")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("content-length", "0"),
                                new Header("connection", "keep-alive")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldQueryRunningStatusWhenNotRunning() throws Exception {
        // given
        int numberOfRetries = 11;
        HttpResponse[] httpResponses = new HttpResponse[numberOfRetries];
        Arrays.fill(httpResponses, response().withStatusCode(404));
        echoServer.withNextResponse(httpResponses);

        // when
        boolean isRunning = mockServerClient.isRunning();

        // then
        assertThat(isRunning, is(false));
        assertThat(requestLogFilter.httpRequests(request()).size(), is(numberOfRetries));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/status")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("content-length", "0"),
                                new Header("connection", "keep-alive")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendResetRequest() throws Exception {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient.reset();

        // then
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/reset")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("content-length", "0"),
                                new Header("connection", "keep-alive")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendClearRequest() throws Exception {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
                .clear(
                        request()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body"))
                );

        // then
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/clear")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
                        .withBody(new StringBody("" +
                                "{\n" +
                                "  \"path\" : \"/some_path\",\n" +
                                "  \"body\" : \"some_request_body\"\n" +
                                "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendClearRequestWithType() throws Exception {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient
                .clear(
                        request()
                                .withPath("/some_path")
                                .withBody(new StringBody("some_request_body")),
                        LOG
                );

        // then
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/clear")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
                        .withBody(new StringBody("" +
                                "{\n" +
                                "  \"path\" : \"/some_path\",\n" +
                                "  \"body\" : \"some_request_body\"\n" +
                                "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldSendClearRequestForNullRequest() throws Exception {
        // given
        echoServer.withNextResponse(response().withStatusCode(201));

        // when
        mockServerClient.clear(null);

        // then
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/clear")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("content-length", "0"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveRequests() {
        // given
        echoServer.withNextResponse(
                response()
                        .withStatusCode(201)
                        .withBody(new StringBody(new HttpRequestSerializer().serialize(Arrays.asList(
                                request("/some_request_one"),
                                request("/some_request_two")
                        ))))
        );

        // when
        HttpRequest[] actualResponse = mockServerClient.retrieveRecordedRequests(
                request()
                        .withPath("/some_path")
                        .withBody(new StringBody("some_request_body"))
        );

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
                request("/some_request_one"),
                request("/some_request_two")
        ));
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/retrieve")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
                        .withBody(new StringBody("{\n" +
                                "  \"path\" : \"/some_path\",\n" +
                                "  \"body\" : \"some_request_body\"\n" +
                                "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveRequestsWithNullRequest() {
        // given
        echoServer.withNextResponse(
                response()
                        .withStatusCode(201)
                        .withBody(new StringBody(new HttpRequestSerializer().serialize(Arrays.asList(
                                request("/some_request_one"),
                                request("/some_request_two")
                        ))))
        );

        // when
        HttpRequest[] actualResponse = mockServerClient.retrieveRecordedRequests(null);

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
                request("/some_request_one"),
                request("/some_request_two")
        ));
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/retrieve")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("content-length", "0"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveSetupExpectations() {
        // given
        echoServer.withNextResponse(
                response()
                        .withStatusCode(201)
                        .withBody(new StringBody(new ExpectationSerializer().serialize(
                                new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
                                new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
                        )))
        );

        // when
        Expectation[] actualResponse = mockServerClient.retrieveExistingExpectations(
                request()
                        .withPath("/some_path")
                        .withBody(new StringBody("some_request_body"))
        );

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
                new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
                new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
        ));
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/retrieve")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withQueryStringParameters(
                                new Parameter("type", "expectation")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
                        .withBody(new StringBody("{\n" +
                                "  \"path\" : \"/some_path\",\n" +
                                "  \"body\" : \"some_request_body\"\n" +
                                "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldRetrieveSetupExpectationsWithNullRequest() {
        // given
        echoServer.withNextResponse(
                response()
                        .withStatusCode(201)
                        .withBody(new StringBody(new ExpectationSerializer().serialize(
                                new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
                                new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
                        )))
        );

        // when
        Expectation[] actualResponse = mockServerClient.retrieveExistingExpectations(null);

        // then
        assertThat(Arrays.asList(actualResponse), hasItems(
                new Expectation(request("/some_request_one"), unlimited(), TimeToLive.unlimited()).thenRespond(response()),
                new Expectation(request("/some_request_two"), unlimited(), TimeToLive.unlimited()).thenRespond(response())
        ));
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/retrieve")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("content-length", "0"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withQueryStringParameters(
                                new Parameter("type", "expectation")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldVerifySingleRequestNoVerificationTimes() {
        // given
        echoServer.withNextResponse(
                response()
                        .withStatusCode(201)
        );

        // when
        mockServerClient.verify(
                request()
                        .withPath("/some_path")
                        .withBody(new StringBody("some_request_body"))
        );

        // then
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/verifySequence")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
                        .withBody(new StringBody("" +
                                "{\n" +
                                "  \"httpRequests\" : [ {\n" +
                                "    \"path\" : \"/some_path\",\n" +
                                "    \"body\" : \"some_request_body\"\n" +
                                "  } ]\n" +
                                "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldVerifyMultipleRequestsNoVerificationTimes() {
        // given
        echoServer.withNextResponse(
                response()
                        .withStatusCode(201)
        );

        // when
        mockServerClient.verify(
                request()
                        .withPath("/some_path")
                        .withBody(new StringBody("some_request_body")),
                request()
                        .withPath("/some_path")
                        .withBody(new StringBody("some_request_body"))
        );

        // then
        System.out.println(new HttpRequestToJavaSerializer().serializeAsJava(3, requestLogFilter.httpRequests(request()).get(0)));
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/verifySequence")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
                        .withBody(new StringBody("" +
                                "{\n" +
                                "  \"httpRequests\" : [ {\n" +
                                "    \"path\" : \"/some_path\",\n" +
                                "    \"body\" : \"some_request_body\"\n" +
                                "  }, {\n" +
                                "    \"path\" : \"/some_path\",\n" +
                                "    \"body\" : \"some_request_body\"\n" +
                                "  } ]\n" +
                                "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

    @Test
    public void shouldVerifySingleRequestOnce() {
        // given
        echoServer.withNextResponse(
                response()
                        .withStatusCode(201)
        );

        // when
        mockServerClient.verify(
                request()
                        .withPath("/some_path")
                        .withBody(new StringBody("some_request_body")),
                VerificationTimes.once()
        );

        // then
        assertThat(requestLogFilter.httpRequests(request()).size(), is(1));
        String result = requestLogFilter.verify(verification().withRequest(
                request()
                        .withMethod("PUT")
                        .withPath("/verify")
                        .withHeaders(
                                new Header("host", "localhost:" + freePort),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("connection", "keep-alive"),
                                new Header("content-type", "text/plain; charset=utf-8")
                        )
                        .withSecure(false)
                        .withKeepAlive(true)
                        .withBody(new StringBody("" +
                                "{\n" +
                                "  \"httpRequest\" : {\n" +
                                "    \"path\" : \"/some_path\",\n" +
                                "    \"body\" : \"some_request_body\"\n" +
                                "  },\n" +
                                "  \"times\" : {\n" +
                                "    \"count\" : 1,\n" +
                                "    \"exact\" : true\n" +
                                "  }\n" +
                                "}"))
        ));
        if (result != null && !result.isEmpty()) {
            throw new AssertionError(result);
        }
    }

}
