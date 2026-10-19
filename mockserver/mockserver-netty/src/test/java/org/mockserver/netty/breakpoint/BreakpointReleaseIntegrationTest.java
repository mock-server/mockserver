package org.mockserver.netty.breakpoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.mock.breakpoint.BreakpointCallbackDispatcher;
import org.mockserver.mock.breakpoint.BreakpointMatcherRegistry;
import org.mockserver.mock.breakpoint.StreamFrameBreakpointRegistry;
import org.mockserver.mock.breakpoint.StreamFrameCallbackDispatcher;
import org.mockserver.netty.MockServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Drives breakpoints over a live server with a raw callback WebSocket client, as the dashboard does,
 * so each assertion is on what a real client and a real caller see:
 * <ul>
 *   <li>a paused exchange the breakpoint timeout continues is announced to its client;</li>
 *   <li>an {@code httpSseResponse} mock pauses at a stream-frame breakpoint;</li>
 *   <li>a forwarded stream whose upstream completes while a frame is held still delivers that frame.</li>
 * </ul>
 */
public class BreakpointReleaseIntegrationTest {

    private static final String TYPE_PAUSED_FRAME = "org.mockserver.serialization.model.PausedStreamFrameDTO";
    private static final String TYPE_RELEASED = "org.mockserver.serialization.model.BreakpointReleasedDTO";
    private static final String TYPE_HTTP_REQUEST = "org.mockserver.model.HttpRequest";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
    private Configuration configuration;
    private MockServer mockServer;
    private HttpClient httpClient;
    private WebSocket webSocket;
    private String clientId;

    @Before
    public void setUp() throws Exception {
        resetBreakpointSingletons();
        configuration = Configuration.configuration().breakpointTimeoutMillis(30_000L);
        mockServer = new MockServer(configuration);
        httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        webSocket = httpClient.newWebSocketBuilder()
            .buildAsync(URI.create("ws://localhost:" + port() + "/_mockserver_callback_websocket?capabilities=breakpointReleased"), new CollectingListener(received))
            .get(10, TimeUnit.SECONDS);
        clientId = clientIdFrom(received);
    }

    @After
    public void tearDown() {
        if (webSocket != null) {
            webSocket.abort();
        }
        stopQuietly(mockServer);
        resetBreakpointSingletons();
    }

    private static void resetBreakpointSingletons() {
        BreakpointMatcherRegistry.getInstance().clear();
        StreamFrameBreakpointRegistry.getInstance().reset();
        BreakpointCallbackDispatcher.getInstance().reset();
        StreamFrameCallbackDispatcher.getInstance().reset();
    }

    @Test(timeout = 60_000)
    public void shouldTellTheClientWhenTheBreakpointTimeoutContinuesAPausedRequest() throws Exception {
        configuration.breakpointTimeoutMillis(500L);
        put("/mockserver/expectation", "{\"httpRequest\":{\"path\":\"/bp\"},\"httpResponse\":{\"statusCode\":200,\"body\":\"from-mock\"}}");
        registerBreakpoint("/bp", "REQUEST");

        CompletableFuture<HttpResponse<String>> caller = get("/bp");
        JsonNode paused = objectMapper.readTree(next(TYPE_HTTP_REQUEST).get("value").asText());
        String correlationId = paused.get("headers").get("WebSocketCorrelationId").get(0).asText();

        JsonNode released = objectMapper.readTree(next(TYPE_RELEASED).get("value").asText());
        assertThat(released.get("correlationId").asText(), is(correlationId));
        assertThat(released.get("reason").asText(), is("TIMEOUT"));
        assertThat(released.get("message").asText(), containsString("500 ms"));
        assertThat(caller.get(30, TimeUnit.SECONDS).body(), is("from-mock"));
    }

    @Test(timeout = 60_000)
    public void shouldSendReleasedNoticesToAClientThatAsksWithAHeader() throws Exception {
        // a language client asks with a header and connects to the bare URI that 8.0.0 servers require
        BlockingQueue<JsonNode> headerClientMessages = new LinkedBlockingQueue<>();
        WebSocket headerClient = httpClient.newWebSocketBuilder()
            .header("X-MockServer-Capabilities", "breakpointReleased")
            .buildAsync(URI.create("ws://localhost:" + port() + "/_mockserver_callback_websocket"), new CollectingListener(headerClientMessages))
            .get(10, TimeUnit.SECONDS);
        try {
            String headerClientId = clientIdFrom(headerClientMessages);
            configuration.breakpointTimeoutMillis(500L);
            put("/mockserver/expectation", "{\"httpRequest\":{\"path\":\"/header\"},\"httpResponse\":{\"statusCode\":200,\"body\":\"from-mock\"}}");
            put("/mockserver/breakpoint/matcher", "{\"httpRequest\":{\"path\":\"/header\"},\"phases\":[\"REQUEST\"],\"clientId\":\"" + headerClientId + "\"}");

            CompletableFuture<HttpResponse<String>> caller = get("/header");
            JsonNode paused = objectMapper.readTree(next(headerClientMessages, TYPE_HTTP_REQUEST).get("value").asText());
            JsonNode released = objectMapper.readTree(next(headerClientMessages, TYPE_RELEASED).get("value").asText());
            assertThat(released.get("correlationId").asText(), is(paused.get("headers").get("WebSocketCorrelationId").get(0).asText()));
            assertThat(caller.get(30, TimeUnit.SECONDS).body(), is("from-mock"));
        } finally {
            headerClient.abort();
        }
    }

    @Test(timeout = 60_000)
    public void shouldNotSendReleasedNoticesToAClientThatDidNotAskForThem() throws Exception {
        // connected as an 8.0.0 client does: no capabilities on the upgrade URI
        BlockingQueue<JsonNode> oldClientMessages = new LinkedBlockingQueue<>();
        WebSocket oldClient = httpClient.newWebSocketBuilder()
            .buildAsync(URI.create("ws://localhost:" + port() + "/_mockserver_callback_websocket"), new CollectingListener(oldClientMessages))
            .get(10, TimeUnit.SECONDS);
        try {
            String oldClientId = clientIdFrom(oldClientMessages);
            configuration.breakpointTimeoutMillis(500L);
            put("/mockserver/expectation", "{\"httpRequest\":{\"path\":\"/old.*\"},\"httpResponse\":{\"statusCode\":200,\"body\":\"from-mock\"}}");
            put("/mockserver/breakpoint/matcher", "{\"httpRequest\":{\"path\":\"/old.*\"},\"phases\":[\"REQUEST\"],\"clientId\":\"" + oldClientId + "\"}");

            CompletableFuture<HttpResponse<String>> first = get("/old-1");
            assertThat(next(oldClientMessages, TYPE_HTTP_REQUEST), is(notNullValue()));
            assertThat("the timeout still continues the request", first.get(30, TimeUnit.SECONDS).body(), is("from-mock"));

            // the next message after the timeout is the second pause, not a notice the client cannot read
            CompletableFuture<HttpResponse<String>> second = get("/old-2");
            JsonNode paused = objectMapper.readTree(next(oldClientMessages, TYPE_HTTP_REQUEST).get("value").asText());
            assertThat(paused.get("path").asText(), is("/old-2"));
            assertThat(second.get(30, TimeUnit.SECONDS).body(), is("from-mock"));
        } finally {
            oldClient.abort();
        }
    }

    @Test(timeout = 60_000)
    public void shouldPauseTheEventsOfAnSseMockAtAStreamFrameBreakpoint() throws Exception {
        // closing the connection at the end of the stream lets the server stop without waiting on it
        put("/mockserver/expectation", "{\"httpRequest\":{\"path\":\"/sse\"},\"httpSseResponse\":{\"closeConnection\":true,\"events\":[{\"data\":\"one\"},{\"data\":\"two\"}]}}");
        registerBreakpoint("/sse", "RESPONSE_STREAM");

        CompletableFuture<HttpResponse<String>> caller = get("/sse");
        JsonNode first = pausedFrame();
        assertThat(decode(first.get("body").asText()), is("data: one\n\n"));
        assertThat("the caller waits for the held event", caller.isDone(), is(false));
        decide(first, "MODIFY", "data: changed\n\n");
        decide(pausedFrame(), "CONTINUE", null);

        assertThat(caller.get(30, TimeUnit.SECONDS).body(), is("data: changed\n\ndata: two\n\n"));
    }

    @Test(timeout = 60_000)
    public void shouldDeliverForwardedFramesHeldWhenTheUpstreamCompletes() throws Exception {
        put("/mockserver/expectation", "{\"httpRequest\":{\"path\":\"/up\"},\"httpSseResponse\":{\"events\":[{\"data\":\"one\"},{\"data\":\"two\"}]}}");
        put("/mockserver/expectation", "{\"httpRequest\":{\"path\":\"/front\"},\"httpOverrideForwardedRequest\":{\"httpRequest\":{\"path\":\"/up\",\"headers\":{\"Host\":[\"127.0.0.1:" + port() + "\"]}}}}");
        registerBreakpoint("/front", "RESPONSE_STREAM");

        CompletableFuture<HttpResponse<String>> caller = get("/front");
        JsonNode held = pausedFrame();
        StringBuilder frames = new StringBuilder(decode(held.get("body").asText()));
        // the upstream sends both events at once, so the stream completes while the first frame is held
        decide(held, "CONTINUE", null);
        while (!frames.toString().contains("data: two")) {
            held = pausedFrame();
            frames.append(decode(held.get("body").asText()));
            decide(held, "CONTINUE", null);
        }

        assertThat(caller.get(30, TimeUnit.SECONDS).body(), is(frames.toString()));
        assertThat(frames.toString(), is("data: one\n\ndata: two\n\n"));
    }

    private int port() {
        return mockServer.getLocalPort();
    }

    private void registerBreakpoint(String path, String phase) throws Exception {
        put("/mockserver/breakpoint/matcher", "{\"httpRequest\":{\"path\":\"" + path + "\"},\"phases\":[\"" + phase + "\"],\"clientId\":\"" + clientId + "\"}");
    }

    private void put(String path, String json) throws Exception {
        HttpResponse<String> response = httpClient.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port() + path))
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(10))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(path + " -> " + response.body(), response.statusCode(), is(201));
    }

    private CompletableFuture<HttpResponse<String>> get(String path) {
        return httpClient.sendAsync(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port() + path)).GET().timeout(Duration.ofSeconds(30)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode pausedFrame() throws Exception {
        return objectMapper.readTree(next(TYPE_PAUSED_FRAME).get("value").asText());
    }

    private void decide(JsonNode pausedFrame, String action, String body) throws Exception {
        ObjectNode decision = objectMapper.createObjectNode()
            .put("correlationId", pausedFrame.get("correlationId").asText())
            .put("action", action);
        if (body != null) {
            decision.put("body", Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
        }
        ObjectNode envelope = objectMapper.createObjectNode()
            .put("type", "org.mockserver.serialization.model.StreamFrameDecisionDTO")
            .put("value", objectMapper.writeValueAsString(decision));
        webSocket.sendText(objectMapper.writeValueAsString(envelope), true).get(10, TimeUnit.SECONDS);
    }

    /**
     * The next callback message of the given type; fails on any other type, so an unexpected
     * notice or a missing pause is reported rather than skipped.
     */
    private JsonNode next(String type) throws Exception {
        return next(received, type);
    }

    private JsonNode next(BlockingQueue<JsonNode> messages, String type) throws Exception {
        JsonNode message = messages.poll(20, TimeUnit.SECONDS);
        assertThat("expected a " + type + " message", message, is(notNullValue()));
        assertThat(message.get("type").asText(), is(type));
        return message;
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private String clientIdFrom(BlockingQueue<JsonNode> messages) throws Exception {
        return objectMapper.readTree(next(messages, "org.mockserver.serialization.model.WebSocketClientIdDTO").get("value").asText()).get("clientId").asText();
    }

    private class CollectingListener implements WebSocket.Listener {
        private final StringBuilder partial = new StringBuilder();
        private final BlockingQueue<JsonNode> messages;

        CollectingListener(BlockingQueue<JsonNode> messages) {
            this.messages = messages;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                try {
                    messages.add(objectMapper.readTree(partial.toString()));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }
    }
}
