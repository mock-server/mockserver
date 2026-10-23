package org.mockserver.netty.integration.mock;

import org.junit.After;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.mock.action.http.TcpChaosRegistry;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.mockserver.netty.integration.PipelinedHttp1Client;
import org.mockserver.netty.integration.PipelinedHttp1Client.Response;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.Delay.delay;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpSseResponse.sseResponse;
import static org.mockserver.model.SseEvent.sseEvent;
import static org.mockserver.model.TcpChaosProfile.tcpChaosProfile;
import static org.mockserver.netty.integration.PipelinedHttp1Client.get;
import static org.mockserver.netty.integration.PipelinedHttp1Client.rawRequest;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Pipelined HTTP/1.1 requests behind a response that closes the connection, behind an exchange that never ends, and
 * across a graceful stop: a request waiting behind a closing response is never answered into the close, a non-pipelined
 * request sent during a delayed close is still answered, a stuck exchange cannot hold the requests behind it forever,
 * and a stop waits for waiting requests, then drops any its drain could not reach.
 */
public class Http1PipeliningClosingAndStopIntegrationTest {

    private static final long SLOW_MILLIS = 300;
    private static final AtomicInteger NEVER_ANSWERED_INVOCATIONS = new AtomicInteger();

    private MockServer mockServer;
    private MockServerClient mockServerClient;

    @After
    public void stopServer() {
        TcpChaosRegistry.getInstance().reset();
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
    }

    private int start(Configuration configuration) {
        mockServer = new MockServer(configuration.logLevel("WARN"), 0);
        mockServerClient = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        mockServerClient.when(request().withPath("/fast")).respond(response("fast"));
        return mockServer.getLocalPort();
    }

    // test 8
    @Test
    public void shouldNotAnswerARequestPipelinedBehindAClosingResponseWhoseHeadSaysKeepAlive() throws Exception {
        assertOnlyTheClosingResponseThenFin(connectionOptions().withCloseSocket(true).withKeepAliveOverride(true));
    }

    @Test
    public void shouldNotAnswerARequestPipelinedBehindAClosingResponseWhoseHeadSaysClose() throws Exception {
        assertOnlyTheClosingResponseThenFin(connectionOptions().withCloseSocket(true).withKeepAliveOverride(false));
    }

    @Test
    public void shouldNotAnswerARequestPipelinedBehindADelayedClose() throws Exception {
        assertOnlyTheClosingResponseThenFin(connectionOptions().withCloseSocket(true).withKeepAliveOverride(true).withCloseSocketDelay(delay(TimeUnit.SECONDS, 1)));
    }

    private void assertOnlyTheClosingResponseThenFin(ConnectionOptions options) throws Exception {
        int port = start(configuration());
        mockServerClient.when(request().withPath("/closing")).respond(response("closing")
            .withDelay(TimeUnit.MILLISECONDS, SLOW_MILLIS)
            .withConnectionOptions(options));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(get("/closing"), get("/fast"));

            assertThat(client.readResponse().body(), is("closing"));
            assertThat("nothing follows the closing response but the close", client.readUntilClosedOrQuiet(5_000), is(""));
            assertThat(client.isClosedByServer(1_000), is(true));
        }
    }

    // test 8, chaos arm: the host's slow close lingers after a response whose head says keep-alive
    @Test
    public void shouldNotAnswerARequestPipelinedBehindAChaosSlowClose() throws Exception {
        TcpChaosRegistry.getInstance().put("localhost", tcpChaosProfile().withSlowCloseDelay(delay(TimeUnit.SECONDS, 1)));
        assertOnlyTheClosingResponseThenFin(connectionOptions().withCloseSocket(true).withKeepAliveOverride(true));
    }

    // test 7, chaos arm: the host's connections are reset after each whole response
    @Test
    public void shouldNotAnswerARequestPipelinedBehindAChaosResetMidResponse() throws Exception {
        int port = start(configuration());
        mockServerClient.when(request().withPath("/reset")).respond(response("reset").withDelay(TimeUnit.MILLISECONDS, SLOW_MILLIS));
        TcpChaosRegistry.getInstance().put("localhost", tcpChaosProfile().withResetMidResponse(true));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(get("/reset"), get("/fast"));

            String wire = client.readUntilClosedOrQuiet(5_000);
            assertThat("no response to the pipelined request: " + wire, wire.contains("fast"), is(false));
            assertThat("the connection was reset or closed", client.isClosedByServer(1_000), is(true));
        }
    }

    // test 8, arm R5-1
    @Test
    public void shouldNotAnswerAnythingPipelinedBehindARequestDroppedWhileClosing() throws Exception {
        int port = start(configuration().maxRequestBodySize(1024 * 1024));
        mockServerClient.when(request().withPath("/closing")).respond(response("closing")
            .withDelay(TimeUnit.MILLISECONDS, SLOW_MILLIS)
            .withConnectionOptions(connectionOptions().withCloseSocket(true).withKeepAliveOverride(true).withCloseSocketDelay(delay(TimeUnit.SECONDS, 1))));
        mockServerClient.when(request().withPath("/upload")).respond(response("uploaded"));
        byte[] body = new byte[3 * 1024 * 1024];
        Arrays.fill(body, (byte) 'u');

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            OutputStream out = client.socket().getOutputStream();
            CompletableFuture<Void> written = CompletableFuture.runAsync(() -> {
                try {
                    out.write((get("/closing") + "POST /upload HTTP/1.1\r\nHost: localhost\r\nContent-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.write(body);
                    out.write(get("/fast").getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                } catch (IOException closedWhileWriting) {
                    // the server closes the connection without reading the rest
                }
            });

            assertThat(client.readResponse().body(), is("closing"));
            assertThat("nothing follows the closing response but the close", client.readUntilClosedOrQuiet(5_000), is(""));
            written.get(10, TimeUnit.SECONDS);
        }
        assertThat("the upload behind the close was not matched", mockServerClient.retrieveRecordedRequests(request().withPath("/upload")).length, is(0));
        assertThat("the request behind the dropped upload was not matched", mockServerClient.retrieveRecordedRequests(request().withPath("/fast")).length, is(0));
    }

    // test 29
    @Test
    public void shouldAnswerARequestSentWithoutPipeliningDuringADelayedClose() throws Exception {
        int port = start(configuration());
        mockServerClient.when(request().withPath("/closing")).respond(response("closing")
            .withConnectionOptions(connectionOptions().withCloseSocket(true).withKeepAliveOverride(true).withCloseSocketDelay(delay(TimeUnit.SECONDS, 1))));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(get("/closing"));
            assertThat(client.readResponse().body(), is("closing"));

            client.pipeline(get("/fast"));
            assertThat("answered before the delayed close", client.readResponse().body(), is("fast"));
            assertThat(client.isClosedByServer(5_000), is(true));
        }
        assertThat(mockServerClient.retrieveRecordedRequests(request().withPath("/fast")).length, is(1));
    }

    // test 29, chaos arm
    @Test
    public void shouldAnswerARequestSentWithoutPipeliningDuringAChaosSlowClose() throws Exception {
        int port = start(configuration());
        TcpChaosRegistry.getInstance().put("localhost", tcpChaosProfile().withSlowCloseDelay(delay(TimeUnit.SECONDS, 1)));
        mockServerClient.when(request().withPath("/closing")).respond(response("closing")
            .withConnectionOptions(connectionOptions().withCloseSocket(true).withKeepAliveOverride(true)));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(get("/closing"));
            assertThat(client.readResponse().body(), is("closing"));

            client.pipeline(get("/fast"));
            assertThat("answered before the slow close", client.readResponse().body(), is("fast"));
            assertThat(client.isClosedByServer(5_000), is(true));
        }
    }

    // test 23
    @Test
    public void shouldCloseAConnectionWhosePipelinedRequestsWaitBehindAnExchangeWithNoProgressForTheIdleTimeout() throws Exception {
        int port = start(configuration().inboundConnectionIdleTimeoutMillis(1_000L));
        mockServerClient.when(request().withPath("/stuck")).respond(callback().withCallbackClass(StuckCallback.class));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            long start = System.currentTimeMillis();
            client.pipeline(get("/stuck"), get("/fast"));

            assertThat("nothing is answered", client.readUntilClosedOrQuiet(4_000), is(""));
            long closedAfter = System.currentTimeMillis() - start;
            assertThat("closed by the backstop, after " + closedAfter + "ms", closedAfter, lessThan(3_000L));
        }
        assertThat("the held request is recorded as the connection closes", awaitRecorded("/fast"), is(1));
    }

    // test 25
    @Test
    public void shouldWaitOnStopForAPipelinedRequestAndAnswerBothInOrder() throws Exception {
        int port = start(configuration().stopDrainMillis(10_000L));
        mockServerClient.when(request().withPath("/slow")).respond(response("slow").withDelay(TimeUnit.MILLISECONDS, 500));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(get("/slow"), get("/fast"));
            awaitRequestsInFlight(2);

            long stopStart = System.currentTimeMillis();
            CompletableFuture<String> stopped = mockServer.stopAsync();
            Response slow = client.readResponse();
            Response fast = client.readResponse();
            stopped.get(10, TimeUnit.SECONDS);

            assertThat(slow.body(), is("slow"));
            assertThat(fast.body(), is("fast"));
            assertThat(client.isClosedByServer(5_000), is(true));
            assertThat("stop completed after both, well within its drain", System.currentTimeMillis() - stopStart, lessThan(5_000L));
        }
    }

    // test 26
    @Test
    public void shouldDropAPipelinedRequestStillWaitingWhenTheStopDrainEnds() throws Exception {
        int port = start(configuration().stopDrainMillis(500L));
        NEVER_ANSWERED_INVOCATIONS.set(0);
        mockServerClient.when(request().withPath("/sse")).respondWithSse(sseResponse()
            .withCloseConnection(false)
            .withEvents(sseEvent().withData("first"), sseEvent().withData("never").withDelay(TimeUnit.SECONDS, 60)));
        mockServerClient.when(request().withPath("/held")).respond(callback().withCallbackClass(CountingCallback.class));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(rawRequest("GET", "/sse", "Accept: text/event-stream"), get("/held"));
            awaitRequestsInFlight(2);

            long stopStart = System.currentTimeMillis();
            mockServer.stopAsync().get(10, TimeUnit.SECONDS);
            long stopMillis = System.currentTimeMillis() - stopStart;

            String wire = client.readUntilClosedOrQuiet(5_000);
            assertThat("stop took " + stopMillis + "ms", stopMillis, lessThan(2_500L));
            assertThat("no second response: " + wire, wire.indexOf("HTTP/1.1 ", 1), is(-1));
        }
        assertThat("the held request was never processed", NEVER_ANSWERED_INVOCATIONS.get(), is(0));
    }

    private void awaitRequestsInFlight(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (mockServer.getRequestsInFlight() != expected) {
            assertThat("requests in flight reach " + expected + ", now " + mockServer.getRequestsInFlight(), System.currentTimeMillis() < deadline, is(true));
            Thread.sleep(10);
        }
    }

    private int awaitRecorded(String path) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        int recorded = 0;
        while (System.currentTimeMillis() < deadline && (recorded = mockServerClient.retrieveRecordedRequests(request().withPath(path)).length) == 0) {
            Thread.sleep(20);
        }
        return recorded;
    }

    public static class StuckCallback implements ExpectationResponseCallback {
        @Override
        public HttpResponse handle(HttpRequest httpRequest) throws InterruptedException {
            Thread.sleep(4_000);
            return response("stuck");
        }
    }

    public static class CountingCallback implements ExpectationResponseCallback {
        @Override
        public HttpResponse handle(HttpRequest httpRequest) {
            NEVER_ANSWERED_INVOCATIONS.incrementAndGet();
            return response("held");
        }
    }
}
