package org.mockserver.netty.http3;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.http3.Http3ErrorCode;
import io.netty.handler.codec.http3.Http3Headers;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpLlmResponse;
import org.mockserver.model.HttpSseResponse;
import org.mockserver.model.HttpWebSocketResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.SseEvent;
import org.mockserver.model.WebSocketMessage;
import org.mockserver.netty.MockServer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.Completion.completion;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.http3.Http3TestServer.startWithHttp3;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * The actions that write their own response to the channel (SSE, a streamed LLM response, WebSocket) over a real
 * HTTP/3 connection. Skipped when the QUIC native library is missing.
 */
@SuppressWarnings("deprecation") // NioEventLoopGroup deprecation in Netty 4.2
public class Http3StreamingActionsIntegrationTest {

    private static final long EVENT_DELAY_MILLIS = 500;
    private static final long SLOW_FIRST_EVENT_MILLIS = 2000;

    private static MockServer mockServer;
    private static MockServerClient mockServerClient;
    private static NioEventLoopGroup clientGroup;

    @BeforeClass
    public static void startServer() {
        boolean available;
        try {
            available = io.netty.handler.codec.quic.Quic.isAvailable();
        } catch (Throwable notLoaded) {
            available = false;
        }
        Assume.assumeTrue("native QUIC transport not available on this platform -- skipping HTTP/3 streaming actions test", available);
        mockServer = startWithHttp3(configuration().http3MaxIdleTimeout(30000L));
        mockServerClient = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        clientGroup = new NioEventLoopGroup(2);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
        if (clientGroup != null) {
            clientGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly(10, TimeUnit.SECONDS);
        }
    }

    @Before
    public void reset() {
        mockServerClient.reset();
    }

    @Test
    public void shouldStreamSseEventsAsDataFramesAndEndTheStream() throws Exception {
        mockServerClient.when(request("/events")).respondWithSse(
            HttpSseResponse.sseResponse()
                .withEvents(
                    SseEvent.sseEvent().withEvent("first").withData("one"),
                    SseEvent.sseEvent().withEvent("second").withData("two").withDelay(Delay.milliseconds(EVENT_DELAY_MILLIS)),
                    SseEvent.sseEvent().withId("3").withData("three")
                )
        );

        try (Http3TestClient client = Http3TestClient.open(clientGroup, mockServer)) {
            long sent = System.nanoTime();
            Http3TestClient.Exchange exchange = client.send(requestHeaders("GET", "/events"));

            assertThat(exchange.status(), is(200));
            assertThat(header(exchange, "content-type"), is("text/event-stream"));
            assertThat(header(exchange, "cache-control"), is("no-cache"));
            // connection-specific fields are malformed on HTTP/3 (RFC 9114 section 4.2)
            assertThat(header(exchange, "connection"), is(nullValue()));
            assertThat(header(exchange, "transfer-encoding"), is(nullValue()));
            assertThat(exchange.body(), is(
                "event: first\ndata: one\n\n" +
                    "event: second\ndata: two\n\n" +
                    "id: 3\ndata: three\n\n"
            ));
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sent), greaterThanOrEqualTo(EVENT_DELAY_MILLIS));
            // one DATA frame per event
            assertThat(exchange.dataFrames(), greaterThanOrEqualTo(3));
            assertThat(exchange.receivedTrailers(), is(false));
        }
    }

    @Test
    public void shouldServeOtherStreamsWhileAnSseEventWaitsOutItsDelay() throws Exception {
        mockServerClient.when(request("/slow-events")).respondWithSse(
            HttpSseResponse.sseResponse()
                .withEvents(SseEvent.sseEvent().withData("late").withDelay(Delay.milliseconds(SLOW_FIRST_EVENT_MILLIS)))
        );
        mockServerClient.when(request("/fast")).respond(response().withBody("fast"));

        try (Http3TestClient client = Http3TestClient.open(clientGroup, mockServer)) {
            long sent = System.nanoTime();
            Http3TestClient.Exchange slow = client.send(requestHeaders("GET", "/slow-events"));
            assertThat(slow.status(), is(200));

            Http3TestClient.Exchange fast = client.send(requestHeaders("GET", "/fast"));
            assertThat(fast.body(), is("fast"));
            assertThat("the SSE event is still waiting out its delay", slow.receivedByteArray().length, is(0));

            assertThat(slow.body(), is("data: late\n\n"));
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sent), greaterThanOrEqualTo(SLOW_FIRST_EVENT_MILLIS));
        }
    }

    @Test
    public void shouldEndTheResponseQuietlyWhenTheClientStopsReadingMidStream() throws Exception {
        mockServerClient.when(request("/abandoned-events")).respondWithSse(
            HttpSseResponse.sseResponse()
                .withEvents(
                    SseEvent.sseEvent().withData("first"),
                    SseEvent.sseEvent().withData("second").withDelay(Delay.milliseconds(EVENT_DELAY_MILLIS)),
                    SseEvent.sseEvent().withData("third").withDelay(Delay.milliseconds(EVENT_DELAY_MILLIS))
                )
        );
        mockServerClient.when(request("/fast")).respond(response().withBody("fast"));

        try (Http3TestClient client = Http3TestClient.open(clientGroup, mockServer)) {
            Http3TestClient.Exchange abandoned = client.start(requestHeaders("GET", "/abandoned-events"));
            abandoned.end();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (abandoned.receivedByteArray().length == 0) {
                assertThat("the first event arrived within 10 seconds", System.nanoTime() < deadline, is(true));
                TimeUnit.MILLISECONDS.sleep(5);
            }

            abandoned.stopReading(Http3ErrorCode.H3_REQUEST_CANCELLED);
            // past both remaining events' delays, so the server has tried to write to the stopped stream
            TimeUnit.MILLISECONDS.sleep(3 * EVENT_DELAY_MILLIS);

            // the connection and the server are still serving
            assertThat(client.send(requestHeaders("GET", "/fast")).body(), is("fast"));
        }
        long chunkFailures = Arrays.stream(mockServerClient.retrieveLogMessagesArray(request("/abandoned-events")))
            .filter(message -> message.contains("streaming chunk"))
            .count();
        assertThat("at most one entry for the abandoned stream, not one per event", chunkFailures, lessThanOrEqualTo(1L));
    }

    @Test
    public void shouldStreamLlmResponse() throws Exception {
        mockServerClient.when(request().withMethod("POST").withPath("/v1/chat/completions")).respondWithLlm(
            HttpLlmResponse.llmResponse()
                .withProvider(Provider.OPENAI)
                .withModel("test-model")
                .withCompletion(completion().withText("streamed over quic").withStreaming(true))
        );

        try (Http3TestClient client = Http3TestClient.open(clientGroup, mockServer)) {
            Http3TestClient.Exchange exchange = client.start(requestHeaders("POST", "/v1/chat/completions").add("content-type", "application/json"));
            exchange.data("{\"model\":\"test-model\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}".getBytes(StandardCharsets.UTF_8));
            exchange.end();

            assertThat(exchange.status(), is(200));
            assertThat(header(exchange, "content-type"), is("text/event-stream"));
            String body = exchange.body();
            assertThat(body, startsWith("data: {"));
            assertThat(body, containsString("chat.completion.chunk"));
            assertThat(body, containsString("data: [DONE]"));
            assertThat(exchange.dataFrames(), greaterThanOrEqualTo(2));
        }
    }

    @Test
    public void shouldRefuseWebSocketResponseWith501() throws Exception {
        mockServerClient.when(request("/ws")).respondWithWebSocket(
            HttpWebSocketResponse.webSocketResponse().withMessage(WebSocketMessage.webSocketMessage("hello"))
        );

        try (Http3TestClient client = Http3TestClient.open(clientGroup, mockServer)) {
            Http3TestClient.Exchange exchange = client.send(requestHeaders("GET", "/ws"));

            assertThat(exchange.status(), is(501));
            assertThat(exchange.body(), containsString("WebSocket mocking is not supported over HTTP/3"));
        }
        assertThat(Arrays.asList(mockServerClient.retrieveLogMessagesArray(null)),
            hasItem(containsString("WebSocket mocking is not supported over HTTP/3")));
    }

    private static String header(Http3TestClient.Exchange exchange, String name) throws Exception {
        CharSequence value = exchange.headers().get(name);
        return value != null ? value.toString() : null;
    }

    private static Http3Headers requestHeaders(String method, String path) {
        return new DefaultHttp3Headers()
            .method(HttpMethod.valueOf(method).asciiName())
            .scheme("https")
            .authority("127.0.0.1:" + mockServer.getHttp3Port())
            .path(path);
    }
}
