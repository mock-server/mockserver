package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.extension.AdminApiExtension;
import org.mockserver.extension.Extension;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.mockserver.netty.MockServerCaTrustTestSupport;
import org.mockserver.netty.integration.PipelinedHttp1Client;
import org.mockserver.netty.integration.PipelinedHttp1Client.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.Delay.delay;
import static org.mockserver.model.FileBody.file;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpSseResponse.sseResponse;
import static org.mockserver.model.HttpTemplate.TemplateType.VELOCITY;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.HttpWebSocketResponse.webSocketResponse;
import static org.mockserver.model.SseEvent.sseEvent;
import static org.mockserver.model.WebSocketMessage.webSocketMessage;
import static org.mockserver.netty.integration.PipelinedHttp1Client.get;
import static org.mockserver.netty.integration.PipelinedHttp1Client.head;
import static org.mockserver.netty.integration.PipelinedHttp1Client.rawRequest;
import static org.mockserver.netty.integration.PipelinedHttp1Client.rawRequestWithBody;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * HTTP/1.1 requests pipelined on one connection are answered in the order they were sent (RFC 9112 §9.3.2),
 * whatever path answers each one and however long it takes.
 * <p>
 * Each test sends its requests in a single {@code write()} and reads the responses back in order.
 */
public class Http1PipelinedResponseOrderIntegrationTest {

    private static final long SLOW_MILLIS = 400;
    private static final int LARGE_FILE_BYTES = 8 * 1024 * 1024;

    @ClassRule
    public static final TemporaryFolder FILES = new TemporaryFolder();

    private static MockServer mockServer;
    private static MockServerClient mockServerClient;
    private static MockServer upstreamServer;
    private static MockServerClient upstreamClient;
    private static int port;
    private static Path largeFile;

    @BeforeClass
    public static void startServers() throws IOException {
        Path staticFiles = FILES.newFolder("static").toPath();
        largeFile = staticFiles.resolve("large.bin");
        byte[] large = new byte[LARGE_FILE_BYTES];
        Arrays.fill(large, (byte) 'f');
        Files.write(largeFile, large);

        upstreamServer = new MockServer(configuration().logLevel("WARN"), 0);
        upstreamClient = new MockServerClient("127.0.0.1", upstreamServer.getLocalPort());

        Configuration configuration = configuration()
            .logLevel("WARN")
            .proxySetup(false)
            // a request that matches nothing is answered from the static files, not forwarded to its Host header
            .attemptToProxyIfNoMatchingExpectation(false)
            .staticFilesDirectory(staticFiles.toString())
            .extensions((Supplier<Extension>) SlowAdminExtension::new);
        mockServer = new MockServer(configuration, 0);
        port = mockServer.getLocalPort();
        mockServerClient = new MockServerClient("127.0.0.1", port);
    }

    @AfterClass
    public static void stopServers() {
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
        stopQuietly(upstreamClient);
        stopQuietly(upstreamServer);
    }

    @Before
    public void resetExpectations() {
        mockServerClient.reset();
        mockServerClient.clearBreakpointMatchers();
        upstreamClient.reset();
        mockServerClient.when(request().withPath("/slow")).respond(response("slow").withDelay(TimeUnit.MILLISECONDS, SLOW_MILLIS));
        mockServerClient.when(request().withPath("/fast")).respond(response("fast"));
    }

    // test 1
    @Test
    public void shouldAnswerAFastRequestAfterADelayedOneSentBeforeIt() throws Exception {
        assertSlowThenFast(get("/slow"), "slow");
    }

    // test 2: one test per class of asynchronous response path
    @Test
    public void shouldAnswerAFastRequestAfterAForwardToADelayedUpstream() throws Exception {
        upstreamClient.when(request().withPath("/forwarded")).respond(response("forwarded").withDelay(TimeUnit.MILLISECONDS, SLOW_MILLIS));
        mockServerClient.when(request().withPath("/forwarded")).forward(forward().withHost("127.0.0.1").withPort(upstreamServer.getLocalPort()));

        assertSlowThenFast(get("/forwarded"), "forwarded");
    }

    @Test
    public void shouldAnswerAFastRequestAfterAResponseTemplate() throws Exception {
        mockServerClient.when(request().withPath("/template")).respond(template(VELOCITY, "{ \"statusCode\": 200, \"body\": \"templated $!request.path\" }"));

        assertSlowThenFast(get("/template"), "templated /template");
    }

    @Test
    public void shouldAnswerAFastRequestAfterAFileBody() throws Exception {
        mockServerClient.when(request().withPath("/file")).respond(response().withBody(file(largeFile.toString())));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(get("/file"), get("/fast"));

            List<Response> responses = client.readResponses(false, false);
            assertThat("the file body is answered first, in full", responses.get(0).body.length, is(LARGE_FILE_BYTES));
            assertThat(responses.get(1).body(), is("fast"));
        }
    }

    @Test
    public void shouldAnswerAFastRequestAfterAStaticFile() throws Exception {
        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(get("/large.bin"), get("/fast"));

            List<Response> responses = client.readResponses(false, false);
            assertThat("the static file is answered first: " + responses.get(0).head, responses.get(0).status, is(200));
            assertThat("the static file is answered first, in full", responses.get(0).body.length, is(LARGE_FILE_BYTES));
            assertThat(responses.get(1).body(), is("fast"));
        }
    }

    @Test
    public void shouldAnswerAFastRequestAfterAnObjectCallback() throws Exception {
        mockServerClient.when(request().withPath("/objectCallback")).respond(callbackRequest -> {
            sleep(SLOW_MILLIS);
            return response("object callback");
        });

        assertSlowThenFast(get("/objectCallback"), "object callback");
    }

    @Test
    public void shouldAnswerAFastRequestAfterAClassCallback() throws Exception {
        mockServerClient.when(request().withPath("/classCallback")).respond(callback().withCallbackClass(SlowClassCallback.class));

        assertSlowThenFast(get("/classCallback"), "class callback");
    }

    @Test
    public void shouldAnswerAFastRequestAfterARequestBreakpointIsContinued() throws Exception {
        mockServerClient.when(request().withPath("/breakpoint")).respond(response("breakpoint"));
        mockServerClient.addBreakpoint(request().withPath("/breakpoint"), pausedRequest -> {
            sleep(300);
            return pausedRequest;
        });

        assertSlowThenFast(get("/breakpoint"), "breakpoint");
    }

    @Test
    public void shouldAnswerAFastRequestAfterAControlPlaneRouteDispatchedOffTheEventLoop() throws Exception {
        String loadOpenApi = rawRequestWithBody("PUT", "/mockserver/openapi",
            "{\"specUrlOrPayload\":\"org/mockserver/openapi/openapi_petstore_example.json\"}",
            "Content-Type: application/json");

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(loadOpenApi, get("/fast"));

            List<Response> responses = client.readResponses(false, false);
            assertThat("the OpenAPI load is answered first: " + responses.get(0), responses.get(0).status, is(201));
            assertThat(responses.get(1).body(), is("fast"));
        }
    }

    @Test
    public void shouldAnswerAFastRequestAfterAnExtensionAdminRoute() throws Exception {
        assertSlowThenFast(get("/mockserver/extensions/slowadmin/anything"), "admin");
    }

    // test 3
    @Test
    public void shouldPairAHeadResponseWithItsRequestBehindADelayedGet() throws Exception {
        mockServerClient.when(request().withPath("/head")).respond(response("head body"));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(get("/slow"), head("/head"), get("/fast"));

            Response slow = client.readResponse(false);
            Response toHead = client.readResponse(true);
            Response fast = client.readResponse(false);
            assertThat(slow.body(), is("slow"));
            assertThat(toHead.status, is(200));
            assertThat("the HEAD response keeps the GET's content-length: " + toHead.head, toHead.header("content-length"), is("9"));
            assertThat("nothing follows the HEAD response's head but the next response", fast.body(), is("fast"));
        }
    }

    @Test
    public void shouldPairADelayedHeadResponseWithItsRequestAheadOfAGet() throws Exception {
        mockServerClient.when(request().withPath("/slowHead")).respond(response("head body").withDelay(TimeUnit.MILLISECONDS, SLOW_MILLIS));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(head("/slowHead"), get("/fast"));

            Response toHead = client.readResponse(true);
            Response fast = client.readResponse(false);
            assertThat("the HEAD response keeps the GET's content-length: " + toHead.head, toHead.header("content-length"), is("9"));
            assertThat(fast.body(), is("fast"));
        }
    }

    // test 4
    @Test
    public void shouldAnswerARequestPipelinedBehindAnSseStreamOnlyAfterTheStreamEnds() throws Exception {
        mockServerClient.when(request().withPath("/sse")).respondWithSse(sseResponse()
            .withCloseConnection(false)
            .withEvents(
                sseEvent().withData("first"),
                sseEvent().withData("last").withDelay(TimeUnit.MILLISECONDS, SLOW_MILLIS)));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(rawRequest("GET", "/sse", "Accept: text/event-stream"), get("/fast"));

            assertFastFollowsTheEndOfTheStream(client.readUntilClosedOrQuiet(2_000), "data: last");
        }
    }

    // test 5
    @Test
    public void shouldAnswerARequestPipelinedBehindAStreamedForwardOnlyAfterTheStreamEnds() throws Exception {
        upstreamClient.when(request().withPath("/streamedForward")).respondWithSse(sseResponse()
            .withCloseConnection(false)
            .withEvents(
                sseEvent().withData("first"),
                sseEvent().withData("last").withDelay(TimeUnit.MILLISECONDS, SLOW_MILLIS)));
        mockServerClient.when(request().withPath("/streamedForward")).forward(forward().withHost("127.0.0.1").withPort(upstreamServer.getLocalPort()));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(rawRequest("GET", "/streamedForward", "Accept: text/event-stream"), get("/fast"));

            Response streamed = client.readResponse();
            Response fast = client.readResponse();
            assertThat(streamed.body(), containsString("data: last"));
            assertThat(fast.body(), is("fast"));
        }
    }

    @Test
    public void shouldAnswerARequestPipelinedBehindAChunkDelayedResponseOnlyAfterItsLastChunk() throws Exception {
        mockServerClient.when(request().withPath("/chunkDelayed")).respond(response("0123456789abcdef")
            .withConnectionOptions(connectionOptions().withChunkSize(4).withChunkDelay(delay(TimeUnit.MILLISECONDS, 150))));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(get("/chunkDelayed"), get("/fast"));

            assertFastFollowsTheEndOfTheStream(client.readUntilClosedOrQuiet(2_000), "cdef");
        }
    }

    // test 9
    @Test
    public void shouldUpgradeAWebSocketPipelinedBehindADelayedGetAfterAnsweringIt() throws Exception {
        mockServerClient.when(request().withPath("/websocket")).respondWithWebSocket(webSocketResponse()
            .withMessage(webSocketMessage("hello")));

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(get("/slow"), rawRequest("GET", "/websocket",
                "Upgrade: websocket",
                "Connection: Upgrade",
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==",
                "Sec-WebSocket-Version: 13"));

            Response slow = client.readResponse();
            Response upgraded = client.readResponse();
            assertThat(slow.body(), is("slow"));
            assertThat(upgraded.status, is(101));
            assertThat("the mocked message arrives as a WebSocket text frame", readTextFrame(client), is("hello"));
        }
    }

    @Test
    public void shouldOpenATunnelPipelinedBehindADelayedGetAfterAnsweringIt() throws Exception {
        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(get("/slow"), rawRequest("CONNECT", "127.0.0.1:" + port));

            Response slow = client.readResponse();
            assertThat(slow.body(), is("slow"));
            assertThat(client.readHead(), startsWith("HTTP/1.1 200"));
            client.pipeline(get("/fast"));
            assertThat("the tunnel works", client.readResponse().body(), is("fast"));
        }
    }

    // test 10
    @Test
    public void shouldAnswerAThousandPipelinedRequestsInOrder() throws Exception {
        mockServerClient.when(request().withPath("/tiny/.*")).respond(template(VELOCITY, "{ \"statusCode\": 200, \"body\": \"$!request.path\" }"));
        int count = 1_000;
        String[] requests = new String[count];
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            requests[i] = get("/tiny/" + i);
            expected.add("/tiny/" + i);
        }

        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(requests);

            List<String> received = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                received.add(client.readResponse().body());
            }
            assertThat(outOfOrderCount(expected, received) + " responses out of order", received, is(expected));
        }
    }

    // test 14
    @Test
    public void shouldAnswerAFastRequestAfterADelayedOneOverTls() throws Exception {
        try (PipelinedHttp1Client client = PipelinedHttp1Client.tls(port, MockServerCaTrustTestSupport.caTrustingSslContext())) {
            client.pipeline(get("/slow"), get("/fast"));

            assertThat(bodies(client.readResponses(false, false)), is(List.of("slow", "fast")));
        }
    }

    // test 15
    @Test
    public void shouldAnswerRequestsPipelinedInsideATunnelInOrder() throws Exception {
        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(rawRequest("CONNECT", "127.0.0.1:" + port));
            assertThat(client.readHead(), startsWith("HTTP/1.1 200"));

            client.pipeline(get("/slow"), get("/fast"));

            assertThat(bodies(client.readResponses(false, false)), is(List.of("slow", "fast")));
        }
    }

    // C-1 row 11 (test 21 pipelined)
    @Test
    public void shouldAnswerABindWithNoBodyAndTheRequestPipelinedBehindIt() throws Exception {
        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(rawRequestWithBody("PUT", "/mockserver/bind", ""), rawRequestWithBody("PUT", "/mockserver/status", ""));

            Response bind = client.readResponse();
            Response status = client.readResponse();
            assertThat("a bind with no body is refused: " + bind, bind.status, is(400));
            assertThat(status.status, is(200));
        }
    }

    private static void assertSlowThenFast(String slowRequest, String slowBody) throws IOException {
        try (PipelinedHttp1Client client = PipelinedHttp1Client.plain(port)) {
            client.pipeline(slowRequest, get("/fast"));

            assertThat(bodies(client.readResponses(false, false)), is(List.of(slowBody, "fast")));
        }
    }

    /**
     * The first response is a stream; the fast response's status line must come only after the stream's last
     * chunk, never inside it.
     */
    private static void assertFastFollowsTheEndOfTheStream(String wire, String lastStreamedText) {
        int lastStreamed = wire.indexOf(lastStreamedText);
        int endOfStream = wire.indexOf("0\r\n\r\n", Math.max(lastStreamed, 0));
        int secondResponse = wire.indexOf("HTTP/1.1 ", 1);
        assertThat("the stream's last content arrived: " + wire, lastStreamed, greaterThan(0));
        assertThat("the stream ended: " + wire, endOfStream, greaterThan(lastStreamed));
        assertThat("the next response follows the stream's end, not inside it: " + wire, secondResponse, is(endOfStream + 5));
        assertThat(wire, containsString("\r\n\r\nfast"));
    }

    private static String readTextFrame(PipelinedHttp1Client client) throws IOException {
        byte[] header = client.readExactly(2);
        assertThat("an unfragmented text frame", header[0] & 0xFF, is(0x81));
        int length = header[1] & 0x7F;
        assertThat("a short, unmasked server frame", length < 126, is(true));
        return new String(client.readExactly(length), StandardCharsets.UTF_8);
    }

    private static List<String> bodies(List<Response> responses) {
        List<String> bodies = new ArrayList<>();
        for (Response response : responses) {
            bodies.add(response.body());
        }
        return bodies;
    }

    private static int outOfOrderCount(List<String> expected, List<String> received) {
        int outOfOrder = 0;
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).equals(received.get(i))) {
                outOfOrder++;
            }
        }
        return outOfOrder;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public static class SlowClassCallback implements ExpectationResponseCallback {
        @Override
        public HttpResponse handle(HttpRequest httpRequest) {
            sleep(SLOW_MILLIS);
            return response("class callback");
        }
    }

    public static class SlowAdminExtension implements AdminApiExtension {
        @Override
        public String name() {
            return "slowadmin";
        }

        @Override
        public HttpResponse handle(HttpRequest request, String subPath) {
            sleep(SLOW_MILLIS);
            return response("admin");
        }
    }
}
