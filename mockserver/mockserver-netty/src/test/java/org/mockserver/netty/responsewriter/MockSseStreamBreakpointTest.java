package org.mockserver.netty.responsewriter;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.llm.StreamingFormat;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.http.HttpSseResponseActionHandler;
import org.mockserver.mock.breakpoint.BreakpointCallbackDispatcher;
import org.mockserver.mock.breakpoint.BreakpointMatcherRegistry;
import org.mockserver.mock.breakpoint.BreakpointPhase;
import org.mockserver.mock.breakpoint.StreamFrameBreakpointRegistry;
import org.mockserver.mock.breakpoint.StreamFrameCallbackDispatcher;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpSseResponse;
import org.mockserver.model.SseEvent;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.WebSocketMessageSerializer;
import org.mockserver.serialization.model.PausedStreamFrameDTO;
import org.mockserver.serialization.model.StreamFrameDecisionDTO;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * A RESPONSE_STREAM breakpoint holds each event of an {@code httpSseResponse} mock, as it does a
 * forwarded stream's frames: an event reaches the client only once its decision arrives over the
 * callback WebSocket, and the stream ends only after every held event is decided.
 */
public class MockSseStreamBreakpointTest {

    private static final String CLIENT_ID = "sse-breakpoint-client";

    private final MockServerLogger mockServerLogger = new MockServerLogger();
    private final WebSocketMessageSerializer serializer = new WebSocketMessageSerializer(mockServerLogger);
    private final Configuration configuration = configuration().breakpointTimeoutMillis(30_000L).breakpointMaxHeld(50);

    private EmbeddedChannel dataChannel;
    private EmbeddedChannel wsChannel;
    private WebSocketClientRegistry registry;

    @Before
    public void setUp() {
        resetBreakpointSingletons();
        ChannelInboundHandlerAdapter dummy = new ChannelInboundHandlerAdapter();
        dataChannel = new EmbeddedChannel(dummy);
        wsChannel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        registry = new WebSocketClientRegistry(configuration, mockServerLogger);
        registry.registerClient(CLIENT_ID, wsChannel.pipeline().firstContext(), true);
        drain(wsChannel);
    }

    @After
    public void tearDown() {
        resetBreakpointSingletons();
        dataChannel.finishAndReleaseAll();
        wsChannel.finishAndReleaseAll();
    }

    private void resetBreakpointSingletons() {
        BreakpointMatcherRegistry.getInstance().clear();
        StreamFrameBreakpointRegistry.getInstance().reset();
        BreakpointCallbackDispatcher.getInstance().reset();
        StreamFrameCallbackDispatcher.getInstance().reset();
    }

    @Test
    public void shouldHoldEachMockEventUntilItIsDecidedAndEndTheStreamAfterTheLast() throws Exception {
        registerStreamBreakpoint();
        startStream("one", "two");

        PausedStreamFrameDTO first = readPausedFrame();
        assertThat(first.getStreamId(), endsWith("-stream"));
        assertThat(first.getPhase(), is(BreakpointPhase.RESPONSE_STREAM.name()));
        assertThat(first.getRequestPath(), is("/sse"));
        assertThat(decode(first.getBody()), is("data: one\n\n"));
        assertThat("a held event is not written", dataChannel.readOutbound(), is(nullValue()));

        deliver(first, "MODIFY", "data: changed\n\n");
        assertThat(readChunk(), is("data: changed\n\n"));

        PausedStreamFrameDTO second = readPausedFrame();
        assertThat(second.getSequenceNumber(), is(1));
        assertThat("the stream does not end while an event is held", dataChannel.readOutbound(), is(nullValue()));

        deliver(second, "CONTINUE", null);
        assertThat(readChunk(), is("data: two\n\n"));
        assertThat(dataChannel.readOutbound(), is(instanceOf(LastHttpContent.class)));
    }

    @Test
    public void shouldApplyDropInjectAndCloseToMockEvents() throws Exception {
        registerStreamBreakpoint();
        startStream("one", "two", "three");

        deliver(readPausedFrame(), "DROP", null);
        deliver(readPausedFrame(), "INJECT", "data: extra\n\n");
        assertThat(readChunk(), is("data: two\n\n"));
        assertThat(readChunk(), is("data: extra\n\n"));

        deliver(readPausedFrame(), "CLOSE", null);
        assertThat("CLOSE ends the stream without the held event", dataChannel.readOutbound(), is(instanceOf(LastHttpContent.class)));
        assertThat(dataChannel.readOutbound(), is(nullValue()));
    }

    @Test
    public void shouldWriteMockEventsAtOnceWithoutAStreamBreakpoint() {
        startStream("one");

        assertThat(readChunk(), is("data: one\n\n"));
        assertThat(dataChannel.readOutbound(), is(instanceOf(LastHttpContent.class)));
        assertThat("nothing is dispatched to the callback client", wsChannel.readOutbound(), is(nullValue()));
    }

    @Test
    public void shouldForgetTheStreamWhenTheClientLeavesDuringAnEventDelay() throws Exception {
        registerStreamBreakpoint();
        startStream(
            SseEvent.sseEvent().withData("one"),
            SseEvent.sseEvent().withData("two").withDelay(new Delay(TimeUnit.SECONDS, 30)));
        PausedStreamFrameDTO first = readPausedFrame();
        deliver(first, "CONTINUE", null);
        assertThat(readChunk(), is("data: one\n\n"));

        // when — the client leaves while the second event waits for its (never-run) delay
        dataChannel.close();
        dataChannel.runPendingTasks();

        // then — the stream's sequence counter is gone: the next number for that id starts again
        assertThat(StreamFrameBreakpointRegistry.getInstance().nextSequenceNumber(first.getStreamId()), is(0));
    }

    @Test
    public void shouldKeepAJsonArrayValidWhenItsFirstElementIsDropped() throws Exception {
        registerStreamBreakpoint();
        startStream(StreamingFormat.JSON_ARRAY, "{\"a\":1}", "{\"b\":2}");

        deliver(readPausedFrame(), "DROP", null);
        deliver(readPausedFrame(), "CONTINUE", null);

        assertThat(readBody(), is("[{\"b\":2}]"));
    }

    @Test
    public void shouldHoldABareJsonArrayElementAndSeparateReplacedAndInjectedOnes() throws Exception {
        registerStreamBreakpoint();
        startStream(StreamingFormat.JSON_ARRAY, "{\"a\":1}", "{\"b\":2}");

        deliver(readPausedFrame(), "INJECT", "{\"x\":0}");
        PausedStreamFrameDTO second = readPausedFrame();
        assertThat("the operator sees the element without its separator", decode(second.getBody()), is("{\"b\":2}"));
        deliver(second, "MODIFY", "{\"c\":3}");

        assertThat(readBody(), is("[{\"a\":1},\r\n{\"x\":0},\r\n{\"c\":3}]"));
    }

    private void registerStreamBreakpoint() {
        BreakpointMatcherRegistry.getInstance().register(
            request().withPath("/sse"),
            EnumSet.of(BreakpointPhase.RESPONSE_STREAM),
            CLIENT_ID,
            configuration,
            mockServerLogger
        );
    }

    private void startStream(String... data) {
        startStream(java.util.Arrays.stream(data).map(event -> SseEvent.sseEvent().withData(event)).toArray(SseEvent[]::new));
    }

    private void startStream(StreamingFormat format, String... data) {
        startStream(format, java.util.Arrays.stream(data).map(event -> SseEvent.sseEvent().withData(event)).toArray(SseEvent[]::new));
    }

    private void startStream(SseEvent... events) {
        startStream(StreamingFormat.SSE, events);
    }

    private void startStream(StreamingFormat format, SseEvent... events) {
        HttpSseResponse sseResponse = HttpSseResponse.sseResponse();
        for (SseEvent event : events) {
            sseResponse.withEvent(event);
        }
        ChannelHandlerContext ctx = dataChannel.pipeline().firstContext();
        new HttpSseResponseActionHandler(mockServerLogger, mock(Scheduler.class), configuration, registry)
            .handle(sseResponse, ctx, request("/sse").withKeepAlive(true), format);
        dataChannel.runPendingTasks();
        Object head = dataChannel.readOutbound();
        assertThat("the response head is written first", head, is(instanceOf(HttpResponse.class)));
        ReferenceCountUtil.release(head);
    }

    private PausedStreamFrameDTO readPausedFrame() throws Exception {
        TextWebSocketFrame frame = wsChannel.readOutbound();
        assertThat("a held event is dispatched over the callback WebSocket", frame, is(notNullValue()));
        Object message = serializer.deserialize(frame.text());
        frame.release();
        assertThat(message, is(instanceOf(PausedStreamFrameDTO.class)));
        return (PausedStreamFrameDTO) message;
    }

    private void deliver(PausedStreamFrameDTO paused, String action, String body) throws Exception {
        StreamFrameDecisionDTO decision = new StreamFrameDecisionDTO()
            .setCorrelationId(paused.getCorrelationId())
            .setAction(action);
        if (body != null) {
            decision.setBody(Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
        }
        registry.receivedTextWebSocketFrame(new TextWebSocketFrame(serializer.serialize(decision)));
        dataChannel.runPendingTasks();
    }

    private String readChunk() {
        Object outbound = dataChannel.readOutbound();
        assertThat("an event must be written", outbound, is(instanceOf(HttpContent.class)));
        assertThat(outbound, is(org.hamcrest.Matchers.not(instanceOf(LastHttpContent.class))));
        HttpContent content = (HttpContent) outbound;
        String body = content.content().toString(StandardCharsets.UTF_8);
        content.release();
        return body;
    }

    /** Reads every content chunk written so far, through the end of the response. */
    private String readBody() {
        StringBuilder body = new StringBuilder();
        Object outbound;
        while ((outbound = dataChannel.readOutbound()) != null) {
            if (outbound instanceof HttpContent) {
                body.append(((HttpContent) outbound).content().toString(StandardCharsets.UTF_8));
            }
            ReferenceCountUtil.release(outbound);
        }
        return body.toString();
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private static void drain(EmbeddedChannel channel) {
        Object outbound;
        while ((outbound = channel.readOutbound()) != null) {
            ReferenceCountUtil.release(outbound);
        }
    }
}
