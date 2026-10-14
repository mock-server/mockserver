package org.mockserver.mock.breakpoint;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.WebSocketMessageSerializer;
import org.mockserver.serialization.model.BreakpointReleasedDTO;
import org.mockserver.serialization.model.PausedStreamFrameDTO;
import org.mockserver.serialization.model.StreamFrameDecisionDTO;

import java.util.Base64;
import java.util.EnumSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpRequest.request;

/**
 * Tests the WS-callback stream frame dispatch path for RESPONSE_STREAM and
 * INBOUND_STREAM phases.
 * <p>
 * Uses a real {@link WebSocketClientRegistry} with an {@link EmbeddedChannel} to
 * simulate a connected callback client, then verifies that client replies
 * produce the correct {@link StreamFrameDecision} outcomes.
 * <p>
 * This test mutates the global singletons {@link StreamFrameCallbackDispatcher},
 * {@link BreakpointMatcherRegistry}, and
 * {@link StreamFrameBreakpointRegistry}, so it must run in the sequential
 * Surefire phase.
 */
public class StreamFrameCallbackDispatcherTest {

    private static final String CLIENT_ID = "test-stream-frame-client";

    private Configuration configuration;
    private MockServerLogger logger;
    private WebSocketClientRegistry webSocketClientRegistry;
    private WebSocketMessageSerializer serializer;
    private EmbeddedChannel clientChannel;
    private StreamFrameCallbackDispatcher dispatcher;

    @Before
    public void setUp() {
        // Clean all breakpoint singletons FIRST, before creating any test infrastructure
        BreakpointMatcherRegistry.getInstance().clear();

        StreamFrameBreakpointRegistry.getInstance().reset();
        BreakpointCallbackDispatcher.getInstance().reset();
        StreamFrameCallbackDispatcher.getInstance().reset();
        // Reset static config properties in case a prior test class changed them
        org.mockserver.configuration.ConfigurationProperties.breakpointTimeoutMillis(30_000);
        org.mockserver.configuration.ConfigurationProperties.breakpointMaxHeld(50);

        configuration = Configuration.configuration()
            .breakpointTimeoutMillis(30_000L)
            .breakpointMaxHeld(50);
        logger = new MockServerLogger();
        webSocketClientRegistry = new WebSocketClientRegistry(configuration, logger);
        serializer = new WebSocketMessageSerializer(logger);
        dispatcher = StreamFrameCallbackDispatcher.getInstance();

        // Register a fake WebSocket client
        clientChannel = new EmbeddedChannel();
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(clientChannel);
        webSocketClientRegistry.registerClient(CLIENT_ID, ctx, true);

        // Drain the registration confirmation message
        clientChannel.readOutbound();
    }

    @After
    public void tearDown() {
        BreakpointMatcherRegistry.getInstance().clear();

        StreamFrameBreakpointRegistry.getInstance().reset();
        BreakpointCallbackDispatcher.getInstance().reset();
        dispatcher.reset();
        if (clientChannel.isOpen()) {
            clientChannel.close();
        }
    }

    // ---- DTO round-trip serialization ----

    @Test
    public void shouldSerializeAndDeserializePausedStreamFrameDTO() throws Exception {
        PausedStreamFrameDTO dto = new PausedStreamFrameDTO()
            .setCorrelationId("corr-1")
            .setStreamId("stream-1")
            .setSequenceNumber(0)
            .setDirection("OUTBOUND")
            .setPhase("RESPONSE_STREAM")
            .setBody(Base64.getEncoder().encodeToString("hello".getBytes()))
            .setRequestMethod("GET")
            .setRequestPath("/api/test");

        String json = serializer.serialize(dto);
        assertThat(json, is(notNullValue()));

        Object deserialized = serializer.deserialize(json);
        assertThat(deserialized, instanceOf(PausedStreamFrameDTO.class));
        PausedStreamFrameDTO result = (PausedStreamFrameDTO) deserialized;
        assertThat(result.getCorrelationId(), is("corr-1"));
        assertThat(result.getStreamId(), is("stream-1"));
        assertThat(result.getSequenceNumber(), is(0));
        assertThat(result.getDirection(), is("OUTBOUND"));
        assertThat(result.getPhase(), is("RESPONSE_STREAM"));
        assertThat(result.getBody(), is(Base64.getEncoder().encodeToString("hello".getBytes())));
        assertThat(result.getRequestMethod(), is("GET"));
        assertThat(result.getRequestPath(), is("/api/test"));
    }

    @Test
    public void shouldSerializeAndDeserializeStreamFrameDecisionDTO() throws Exception {
        StreamFrameDecisionDTO dto = new StreamFrameDecisionDTO()
            .setCorrelationId("corr-2")
            .setAction("MODIFY")
            .setBody(Base64.getEncoder().encodeToString("modified".getBytes()));

        String json = serializer.serialize(dto);
        assertThat(json, is(notNullValue()));

        Object deserialized = serializer.deserialize(json);
        assertThat(deserialized, instanceOf(StreamFrameDecisionDTO.class));
        StreamFrameDecisionDTO result = (StreamFrameDecisionDTO) deserialized;
        assertThat(result.getCorrelationId(), is("corr-2"));
        assertThat(result.getAction(), is("MODIFY"));
        assertThat(result.getBody(), is(Base64.getEncoder().encodeToString("modified".getBytes())));
    }

    // ---- CONTINUE action ----

    @Test
    public void shouldContinueWhenClientRepliesContinue() throws Exception {
        byte[] frameBody = "original frame".getBytes();

        CompletableFuture<StreamFrameDecision> future = dispatcher.dispatchFrame(
            CLIENT_ID, null, "stream-1", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frameBody, "GET", "/api/test",
            null, webSocketClientRegistry, configuration, logger
        );
        assertThat(future, is(notNullValue()));
        assertThat(future.isDone(), is(false));

        // Read what was sent to the client
        TextWebSocketFrame sentFrame = clientChannel.readOutbound();
        assertThat(sentFrame, is(notNullValue()));
        Object sentMessage = serializer.deserialize(sentFrame.text());
        assertThat(sentMessage, instanceOf(PausedStreamFrameDTO.class));
        PausedStreamFrameDTO sentDto = (PausedStreamFrameDTO) sentMessage;
        String correlationId = sentDto.getCorrelationId();

        // Client replies with CONTINUE
        StreamFrameDecisionDTO reply = new StreamFrameDecisionDTO()
            .setCorrelationId(correlationId)
            .setAction("CONTINUE");
        webSocketClientRegistry.receivedTextWebSocketFrame(
            new TextWebSocketFrame(serializer.serialize(reply)));

        StreamFrameDecision decision = future.get(5, TimeUnit.SECONDS);
        assertThat(decision.getAction(), is(StreamFrameDecision.Action.CONTINUE));
    }

    // ---- MODIFY action ----

    @Test
    public void shouldModifyWhenClientRepliesModify() throws Exception {
        byte[] frameBody = "original".getBytes();

        CompletableFuture<StreamFrameDecision> future = dispatcher.dispatchFrame(
            CLIENT_ID, null, "stream-1", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frameBody, "GET", "/api/test",
            null, webSocketClientRegistry, configuration, logger
        );
        assertThat(future, is(notNullValue()));

        TextWebSocketFrame sentFrame = clientChannel.readOutbound();
        PausedStreamFrameDTO sentDto = (PausedStreamFrameDTO) serializer.deserialize(sentFrame.text());
        String correlationId = sentDto.getCorrelationId();

        // Client replies with MODIFY
        byte[] modifiedBody = "modified body".getBytes();
        StreamFrameDecisionDTO reply = new StreamFrameDecisionDTO()
            .setCorrelationId(correlationId)
            .setAction("MODIFY")
            .setBody(Base64.getEncoder().encodeToString(modifiedBody));
        webSocketClientRegistry.receivedTextWebSocketFrame(
            new TextWebSocketFrame(serializer.serialize(reply)));

        StreamFrameDecision decision = future.get(5, TimeUnit.SECONDS);
        assertThat(decision.getAction(), is(StreamFrameDecision.Action.MODIFY));
        assertThat(decision.getReplacementBody(), is(modifiedBody));
    }

    // ---- DROP action ----

    @Test
    public void shouldDropWhenClientRepliesDrop() throws Exception {
        byte[] frameBody = "drop me".getBytes();

        CompletableFuture<StreamFrameDecision> future = dispatcher.dispatchFrame(
            CLIENT_ID, null, "stream-1", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frameBody, "GET", "/api/test",
            null, webSocketClientRegistry, configuration, logger
        );

        TextWebSocketFrame sentFrame = clientChannel.readOutbound();
        PausedStreamFrameDTO sentDto = (PausedStreamFrameDTO) serializer.deserialize(sentFrame.text());

        StreamFrameDecisionDTO reply = new StreamFrameDecisionDTO()
            .setCorrelationId(sentDto.getCorrelationId())
            .setAction("DROP");
        webSocketClientRegistry.receivedTextWebSocketFrame(
            new TextWebSocketFrame(serializer.serialize(reply)));

        StreamFrameDecision decision = future.get(5, TimeUnit.SECONDS);
        assertThat(decision.getAction(), is(StreamFrameDecision.Action.DROP));
    }

    // ---- INJECT action ----

    @Test
    public void shouldInjectWhenClientRepliesInject() throws Exception {
        byte[] frameBody = "original".getBytes();

        CompletableFuture<StreamFrameDecision> future = dispatcher.dispatchFrame(
            CLIENT_ID, null, "stream-1", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frameBody, "GET", "/api/test",
            null, webSocketClientRegistry, configuration, logger
        );

        TextWebSocketFrame sentFrame = clientChannel.readOutbound();
        PausedStreamFrameDTO sentDto = (PausedStreamFrameDTO) serializer.deserialize(sentFrame.text());

        byte[] injectedBody = "injected extra".getBytes();
        StreamFrameDecisionDTO reply = new StreamFrameDecisionDTO()
            .setCorrelationId(sentDto.getCorrelationId())
            .setAction("INJECT")
            .setBody(Base64.getEncoder().encodeToString(injectedBody));
        webSocketClientRegistry.receivedTextWebSocketFrame(
            new TextWebSocketFrame(serializer.serialize(reply)));

        StreamFrameDecision decision = future.get(5, TimeUnit.SECONDS);
        assertThat(decision.getAction(), is(StreamFrameDecision.Action.INJECT));
        assertThat(decision.getInjectedBody(), is(injectedBody));
    }

    // ---- CLOSE action ----

    @Test
    public void shouldCloseWhenClientRepliesClose() throws Exception {
        byte[] frameBody = "close this".getBytes();

        CompletableFuture<StreamFrameDecision> future = dispatcher.dispatchFrame(
            CLIENT_ID, null, "stream-1", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frameBody, "GET", "/api/test",
            null, webSocketClientRegistry, configuration, logger
        );

        TextWebSocketFrame sentFrame = clientChannel.readOutbound();
        PausedStreamFrameDTO sentDto = (PausedStreamFrameDTO) serializer.deserialize(sentFrame.text());

        StreamFrameDecisionDTO reply = new StreamFrameDecisionDTO()
            .setCorrelationId(sentDto.getCorrelationId())
            .setAction("CLOSE");
        webSocketClientRegistry.receivedTextWebSocketFrame(
            new TextWebSocketFrame(serializer.serialize(reply)));

        StreamFrameDecision decision = future.get(5, TimeUnit.SECONDS);
        assertThat(decision.getAction(), is(StreamFrameDecision.Action.CLOSE));
    }

    // ---- Timeout auto-continue ----

    @Test
    public void shouldAutoContinueOnTimeout() throws Exception {
        Configuration shortTimeoutConfig = Configuration.configuration();
        shortTimeoutConfig.breakpointTimeoutMillis(200L);

        byte[] frameBody = "timeout me".getBytes();

        CompletableFuture<StreamFrameDecision> future = dispatcher.dispatchFrame(
            CLIENT_ID, null, "stream-1", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frameBody, "GET", "/api/test",
            null, webSocketClientRegistry, shortTimeoutConfig, logger
        );
        assertThat(future, is(notNullValue()));

        // Don't reply — let it timeout
        StreamFrameDecision decision = future.get(5, TimeUnit.SECONDS);
        assertThat(decision.getAction(), is(StreamFrameDecision.Action.CONTINUE));
    }

    @Test
    public void timeoutTellsTheOwningClientTheFrameWasContinued() throws Exception {
        BlockingQueue<Object> sent = new LinkedBlockingQueue<>();
        registerCapturingClient("released-frame-client", sent);
        Configuration shortTimeoutConfig = Configuration.configuration().breakpointTimeoutMillis(200L);

        CompletableFuture<StreamFrameDecision> future = dispatcher.dispatchFrame(
            "released-frame-client", null, "stream-timeout", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            "timeout me".getBytes(), "GET", "/api/test",
            null, webSocketClientRegistry, shortTimeoutConfig, logger
        );
        PausedStreamFrameDTO paused = (PausedStreamFrameDTO) serializer.deserialize(((TextWebSocketFrame) sent.poll(5, TimeUnit.SECONDS)).text());

        assertThat(future.get(5, TimeUnit.SECONDS).getAction(), is(StreamFrameDecision.Action.CONTINUE));
        BreakpointReleasedDTO released = awaitReleased(sent);
        assertThat(released.getCorrelationId(), is(paused.getCorrelationId()));
        assertThat(released.getReason(), is(BreakpointReleasedDTO.REASON_TIMEOUT));
    }

    @Test
    public void releaseStreamDropsOnlyThatStreamsHeldFramesAndTellsTheClient() throws Exception {
        CompletableFuture<StreamFrameDecision> ended = dispatcher.dispatchFrame(
            CLIENT_ID, null, "stream-ended", 0, PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            "a".getBytes(), "GET", "/a", null, webSocketClientRegistry, configuration, logger);
        PausedStreamFrameDTO endedFrame = readPausedFrame();
        CompletableFuture<StreamFrameDecision> other = dispatcher.dispatchFrame(
            CLIENT_ID, null, "stream-other", 0, PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            "b".getBytes(), "GET", "/b", null, webSocketClientRegistry, configuration, logger);
        readPausedFrame();

        assertThat(dispatcher.releaseStream("stream-ended"), is(1));

        assertThat(ended.getNow(null).getAction(), is(StreamFrameDecision.Action.DROP));
        assertThat("a frame of another stream stays held", other.isDone(), is(false));
        TextWebSocketFrame frame = clientChannel.readOutbound();
        assertThat("the owning client is told the frame was dropped", frame, is(notNullValue()));
        BreakpointReleasedDTO released = (BreakpointReleasedDTO) serializer.deserialize(frame.text());
        frame.release();
        assertThat(released.getCorrelationId(), is(endedFrame.getCorrelationId()));
        assertThat(released.getReason(), is(BreakpointReleasedDTO.REASON_STREAM_ENDED));
        assertThat("nothing is sent for the frame that stays held", clientChannel.readOutbound(), is(nullValue()));
        assertThat("a released stream has nothing left to release", dispatcher.releaseStream("stream-ended"), is(0));
    }

    @Test
    public void aClientThatDidNotAskForReleasedNoticesIsNeverSentOne() throws Exception {
        // registered as an 8.0.0 client does: no breakpointReleased capability
        EmbeddedChannel oldClientChannel = new EmbeddedChannel();
        ChannelHandlerContext oldCtx = mock(ChannelHandlerContext.class);
        when(oldCtx.channel()).thenReturn(oldClientChannel);
        webSocketClientRegistry.registerClient("old-client", oldCtx);
        ((TextWebSocketFrame) oldClientChannel.readOutbound()).release();
        CompletableFuture<StreamFrameDecision> held = dispatcher.dispatchFrame(
            "old-client", null, "stream-old", 0, PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            "a".getBytes(), "GET", "/a", null, webSocketClientRegistry, configuration, logger);
        ((TextWebSocketFrame) oldClientChannel.readOutbound()).release();

        assertThat(dispatcher.releaseStream("stream-old"), is(1));

        assertThat(held.getNow(null).getAction(), is(StreamFrameDecision.Action.DROP));
        assertThat("an old client is sent nothing it cannot read", oldClientChannel.readOutbound(), is(nullValue()));
        assertThat(webSocketClientRegistry.sendBreakpointReleased("old-client", new BreakpointReleasedDTO().setCorrelationId("x")), is(false));
        oldClientChannel.close();
    }

    private PausedStreamFrameDTO readPausedFrame() throws Exception {
        TextWebSocketFrame frame = clientChannel.readOutbound();
        assertThat(frame, is(notNullValue()));
        PausedStreamFrameDTO paused = (PausedStreamFrameDTO) serializer.deserialize(frame.text());
        frame.release();
        return paused;
    }

    /**
     * Registers a client whose channel records every message written to it in a thread-safe queue,
     * because the timeout notice is written from the timeout scheduler's thread.
     */
    private void registerCapturingClient(String clientId, BlockingQueue<Object> sent) {
        io.netty.channel.Channel channel = mock(io.netty.channel.Channel.class);
        when(channel.writeAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return null;
        });
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(channel);
        webSocketClientRegistry.registerClient(clientId, ctx, true);
        sent.clear();
    }

    private BreakpointReleasedDTO awaitReleased(BlockingQueue<Object> sent) throws Exception {
        TextWebSocketFrame frame = (TextWebSocketFrame) sent.poll(5, TimeUnit.SECONDS);
        assertThat("a released notice must be sent to the owning client", frame, is(notNullValue()));
        Object message = serializer.deserialize(frame.text());
        assertThat(message, instanceOf(BreakpointReleasedDTO.class));
        return (BreakpointReleasedDTO) message;
    }

    // ---- Client not connected ----

    @Test
    public void shouldReturnNullWhenClientNotConnected() {
        byte[] frameBody = "no client".getBytes();

        CompletableFuture<StreamFrameDecision> future = dispatcher.dispatchFrame(
            "nonexistent-client", null, "stream-1", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frameBody, "GET", "/api/test",
            null, webSocketClientRegistry, configuration, logger
        );
        assertThat("should return null when client not connected", future, is(nullValue()));
    }

    // ---- Max-held cap ----

    @Test
    public void shouldReturnNullWhenMaxHeldCapReached() {
        Configuration lowCapConfig = Configuration.configuration();
        lowCapConfig.breakpointMaxHeld(1);

        byte[] frame1 = "first".getBytes();
        byte[] frame2 = "second".getBytes();

        CompletableFuture<StreamFrameDecision> future1 = dispatcher.dispatchFrame(
            CLIENT_ID, null, "stream-1", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frame1, "GET", "/api/test",
            null, webSocketClientRegistry, lowCapConfig, logger
        );
        assertThat(future1, is(notNullValue()));

        CompletableFuture<StreamFrameDecision> future2 = dispatcher.dispatchFrame(
            CLIENT_ID, null, "stream-1", 1,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frame2, "GET", "/api/test",
            null, webSocketClientRegistry, lowCapConfig, logger
        );
        assertThat("should return null when cap is reached", future2, is(nullValue()));

        // Clean up
        future1.complete(StreamFrameDecision.continueFrame());
    }

    // ---- Disconnect auto-continue ----

    @Test
    public void disconnectShouldAutoContinueInFlightDispatches() throws Exception {
        byte[] frameBody = "in-flight".getBytes();

        CompletableFuture<StreamFrameDecision> future = dispatcher.dispatchFrame(
            CLIENT_ID, null, "stream-1", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frameBody, "GET", "/api/test",
            null, webSocketClientRegistry, configuration, logger
        );
        assertThat(future, is(notNullValue()));
        assertThat(future.isDone(), is(false));
        assertThat(dispatcher.inFlightCount(), is(1));

        int autoContinued = dispatcher.autoCompleteForClient(CLIENT_ID);
        assertThat(autoContinued, is(1));

        StreamFrameDecision decision = future.get(5, TimeUnit.SECONDS);
        assertThat(decision.getAction(), is(StreamFrameDecision.Action.CONTINUE));
    }

    // ---- Inbound frame dispatch ----

    @Test
    public void shouldDispatchInboundFrame() throws Exception {
        byte[] frameBody = "inbound data".getBytes();

        CompletableFuture<StreamFrameDecision> future = dispatcher.dispatchFrame(
            CLIENT_ID, null, "inbound-stream-1", 0,
            PausedStreamFrame.Direction.INBOUND, BreakpointPhase.INBOUND_STREAM,
            frameBody, "POST", "/grpc/service/Method",
            null, webSocketClientRegistry, configuration, logger
        );
        assertThat(future, is(notNullValue()));

        TextWebSocketFrame sentFrame = clientChannel.readOutbound();
        PausedStreamFrameDTO sentDto = (PausedStreamFrameDTO) serializer.deserialize(sentFrame.text());
        assertThat(sentDto.getDirection(), is("INBOUND"));
        assertThat(sentDto.getPhase(), is("INBOUND_STREAM"));
        assertThat(sentDto.getStreamId(), is("inbound-stream-1"));

        // Verify body is base64 encoded
        byte[] decodedBody = Base64.getDecoder().decode(sentDto.getBody());
        assertThat(decodedBody, is(frameBody));

        // Client replies with CONTINUE
        StreamFrameDecisionDTO reply = new StreamFrameDecisionDTO()
            .setCorrelationId(sentDto.getCorrelationId())
            .setAction("CONTINUE");
        webSocketClientRegistry.receivedTextWebSocketFrame(
            new TextWebSocketFrame(serializer.serialize(reply)));

        StreamFrameDecision decision = future.get(5, TimeUnit.SECONDS);
        assertThat(decision.getAction(), is(StreamFrameDecision.Action.CONTINUE));
    }

    // ---- tryWsDispatch helper ----

    @Test
    public void tryWsDispatchShouldReturnNullWhenClientIdIsNull() {
        BreakpointMatcher matcherWithoutClientId = new BreakpointMatcher(
            "id-1", request().withPath("/test"),
            EnumSet.of(BreakpointPhase.RESPONSE_STREAM),
            null, null // no clientId
        );

        CompletableFuture<StreamFrameDecision> result = dispatcher.tryWsDispatch(
            matcherWithoutClientId, "stream-1", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            "test".getBytes(), "GET", "/test", configuration, logger, webSocketClientRegistry
        );
        assertThat("should return null when clientId is null", result, is(nullValue()));
    }

    @Test
    public void tryWsDispatchShouldReturnNullWhenRegistryNotSet() {
        BreakpointMatcher matcherWithClientId = new BreakpointMatcher(
            "id-1", request().withPath("/test"),
            EnumSet.of(BreakpointPhase.RESPONSE_STREAM),
            null, CLIENT_ID
        );

        // Pass null registry -- should return null
        CompletableFuture<StreamFrameDecision> result = dispatcher.tryWsDispatch(
            matcherWithClientId, "stream-1", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            "test".getBytes(), "GET", "/test", configuration, logger, null
        );
        assertThat("should return null when registry not set", result, is(nullValue()));
    }

    // ---- mapDecision ----

    @Test
    public void mapDecisionShouldHandleAllActions() {
        assertThat(StreamFrameCallbackDispatcher.mapDecision(
            new StreamFrameDecisionDTO().setAction("CONTINUE")).getAction(),
            is(StreamFrameDecision.Action.CONTINUE));

        byte[] body = "test".getBytes();
        String b64 = Base64.getEncoder().encodeToString(body);
        StreamFrameDecision modify = StreamFrameCallbackDispatcher.mapDecision(
            new StreamFrameDecisionDTO().setAction("MODIFY").setBody(b64));
        assertThat(modify.getAction(), is(StreamFrameDecision.Action.MODIFY));
        assertThat(modify.getReplacementBody(), is(body));

        assertThat(StreamFrameCallbackDispatcher.mapDecision(
            new StreamFrameDecisionDTO().setAction("DROP")).getAction(),
            is(StreamFrameDecision.Action.DROP));

        StreamFrameDecision inject = StreamFrameCallbackDispatcher.mapDecision(
            new StreamFrameDecisionDTO().setAction("INJECT").setBody(b64));
        assertThat(inject.getAction(), is(StreamFrameDecision.Action.INJECT));
        assertThat(inject.getInjectedBody(), is(body));

        assertThat(StreamFrameCallbackDispatcher.mapDecision(
            new StreamFrameDecisionDTO().setAction("CLOSE")).getAction(),
            is(StreamFrameDecision.Action.CLOSE));
    }

    @Test
    public void mapDecisionShouldDefaultToContinueForUnknownAction() {
        assertThat(StreamFrameCallbackDispatcher.mapDecision(
            new StreamFrameDecisionDTO().setAction("UNKNOWN")).getAction(),
            is(StreamFrameDecision.Action.CONTINUE));
    }

    @Test
    public void mapDecisionShouldDefaultToContinueForNullDto() {
        assertThat(StreamFrameCallbackDispatcher.mapDecision(null).getAction(),
            is(StreamFrameDecision.Action.CONTINUE));
    }

    @Test
    public void mapDecisionShouldDefaultToContinueWhenModifyHasNoBody() {
        assertThat(StreamFrameCallbackDispatcher.mapDecision(
            new StreamFrameDecisionDTO().setAction("MODIFY")).getAction(),
            is(StreamFrameDecision.Action.CONTINUE));
    }

    // ---- Sent DTO field verification ----

    @Test
    public void shouldSendCorrectDTOFields() throws Exception {
        byte[] frameBody = "test payload".getBytes();

        dispatcher.dispatchFrame(
            CLIENT_ID, null, "my-stream", 3,
            PausedStreamFrame.Direction.INBOUND, BreakpointPhase.INBOUND_STREAM,
            frameBody, "POST", "/grpc/service",
            null, webSocketClientRegistry, configuration, logger
        );

        TextWebSocketFrame sentFrame = clientChannel.readOutbound();
        PausedStreamFrameDTO sentDto = (PausedStreamFrameDTO) serializer.deserialize(sentFrame.text());

        assertThat(sentDto.getCorrelationId(), is(notNullValue()));
        assertThat(sentDto.getStreamId(), is("my-stream"));
        assertThat(sentDto.getSequenceNumber(), is(3));
        assertThat(sentDto.getDirection(), is("INBOUND"));
        assertThat(sentDto.getPhase(), is("INBOUND_STREAM"));
        assertThat(sentDto.getBody(), is(Base64.getEncoder().encodeToString(frameBody)));
        assertThat(sentDto.getRequestMethod(), is("POST"));
        assertThat(sentDto.getRequestPath(), is("/grpc/service"));
    }

    // ---- breakpointId wiring (MAJOR 1 fix) ----

    @Test
    public void shouldSetBreakpointIdOnDispatchedDTO() throws Exception {
        byte[] frameBody = "breakpoint-id-test".getBytes();
        String breakpointId = "bp-matcher-123";

        dispatcher.dispatchFrame(
            CLIENT_ID, breakpointId, "stream-bp-id", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frameBody, "GET", "/api/test",
            null, webSocketClientRegistry, configuration, logger
        );

        TextWebSocketFrame sentFrame = clientChannel.readOutbound();
        PausedStreamFrameDTO sentDto = (PausedStreamFrameDTO) serializer.deserialize(sentFrame.text());

        assertThat("breakpointId must be non-null and equal to the registered breakpoint id",
            sentDto.getBreakpointId(), is(breakpointId));
    }

    @Test
    public void shouldSendNullBreakpointIdWhenNotProvided() throws Exception {
        byte[] frameBody = "no-breakpoint-id-test".getBytes();

        dispatcher.dispatchFrame(
            CLIENT_ID, null, "stream-no-bp-id", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frameBody, "GET", "/api/test",
            null, webSocketClientRegistry, configuration, logger
        );

        TextWebSocketFrame sentFrame = clientChannel.readOutbound();
        PausedStreamFrameDTO sentDto = (PausedStreamFrameDTO) serializer.deserialize(sentFrame.text());

        assertThat("breakpointId should be null when not provided",
            sentDto.getBreakpointId(), is(nullValue()));
    }

    // ---- Multi-client / multi-breakpoint routing (MINOR 3 fix) ----

    @Test
    public void shouldRouteFrameToCorrectClientWithBreakpointId() throws Exception {
        // Register a second client
        String client2Id = "test-stream-frame-client-2";
        EmbeddedChannel client2Channel = new EmbeddedChannel();
        ChannelHandlerContext ctx2 = mock(ChannelHandlerContext.class);
        when(ctx2.channel()).thenReturn(client2Channel);
        webSocketClientRegistry.registerClient(client2Id, ctx2);
        client2Channel.readOutbound(); // drain registration message

        byte[] frameBody = "multi-client-frame".getBytes();
        String bp1Id = "bp-client1";
        String bp2Id = "bp-client2";

        // Dispatch a frame to client 1 with bp1Id
        CompletableFuture<StreamFrameDecision> future1 = dispatcher.dispatchFrame(
            CLIENT_ID, bp1Id, "stream-mc-1", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frameBody, "GET", "/api/client1",
            null, webSocketClientRegistry, configuration, logger
        );
        assertThat(future1, is(notNullValue()));

        // Dispatch a frame to client 2 with bp2Id
        CompletableFuture<StreamFrameDecision> future2 = dispatcher.dispatchFrame(
            client2Id, bp2Id, "stream-mc-2", 0,
            PausedStreamFrame.Direction.INBOUND, BreakpointPhase.INBOUND_STREAM,
            frameBody, "POST", "/api/client2",
            null, webSocketClientRegistry, configuration, logger
        );
        assertThat(future2, is(notNullValue()));

        // Verify client 1 received a frame with bp1Id
        TextWebSocketFrame sent1 = clientChannel.readOutbound();
        PausedStreamFrameDTO dto1 = (PausedStreamFrameDTO) serializer.deserialize(sent1.text());
        assertThat("client 1 should receive breakpointId=bp-client1", dto1.getBreakpointId(), is(bp1Id));
        assertThat(dto1.getRequestPath(), is("/api/client1"));

        // Verify client 2 received a frame with bp2Id
        TextWebSocketFrame sent2 = client2Channel.readOutbound();
        PausedStreamFrameDTO dto2 = (PausedStreamFrameDTO) serializer.deserialize(sent2.text());
        assertThat("client 2 should receive breakpointId=bp-client2", dto2.getBreakpointId(), is(bp2Id));
        assertThat(dto2.getRequestPath(), is("/api/client2"));

        // Resolve both
        StreamFrameDecisionDTO reply1 = new StreamFrameDecisionDTO()
            .setCorrelationId(dto1.getCorrelationId())
            .setAction("CONTINUE");
        webSocketClientRegistry.receivedTextWebSocketFrame(
            new TextWebSocketFrame(serializer.serialize(reply1)));

        StreamFrameDecisionDTO reply2 = new StreamFrameDecisionDTO()
            .setCorrelationId(dto2.getCorrelationId())
            .setAction("CONTINUE");
        webSocketClientRegistry.receivedTextWebSocketFrame(
            new TextWebSocketFrame(serializer.serialize(reply2)));

        assertThat(future1.get(5, TimeUnit.SECONDS).getAction(), is(StreamFrameDecision.Action.CONTINUE));
        assertThat(future2.get(5, TimeUnit.SECONDS).getAction(), is(StreamFrameDecision.Action.CONTINUE));

        // cleanup
        if (client2Channel.isOpen()) {
            client2Channel.close();
        }
    }

    // ---- requestTimestamp wiring ----

    @Test
    public void shouldSetRequestTimestampOnDispatchedDTO() throws Exception {
        byte[] frameBody = "timestamp-test".getBytes();
        Long requestTimestamp = 1717000000456L;

        dispatcher.dispatchFrame(
            CLIENT_ID, "bp-ts", "stream-ts", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frameBody, "GET", "/api/ts-test",
            requestTimestamp,
            webSocketClientRegistry, configuration, logger
        );

        TextWebSocketFrame sentFrame = clientChannel.readOutbound();
        PausedStreamFrameDTO sentDto = (PausedStreamFrameDTO) serializer.deserialize(sentFrame.text());

        assertThat("requestTimestamp must be present on the dispatched DTO",
            sentDto.getRequestTimestamp(), is(requestTimestamp));

        // cleanup — the dispatch future is still in-flight, auto-continue it
        StreamFrameCallbackDispatcher.getInstance().reset();
    }

    @Test
    public void shouldSetNullRequestTimestampWhenNotProvided() throws Exception {
        byte[] frameBody = "no-ts".getBytes();

        dispatcher.dispatchFrame(
            CLIENT_ID, null, "stream-no-ts", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            frameBody, "GET", "/api/no-ts",
            null, webSocketClientRegistry, configuration, logger
        );

        TextWebSocketFrame sentFrame = clientChannel.readOutbound();
        PausedStreamFrameDTO sentDto = (PausedStreamFrameDTO) serializer.deserialize(sentFrame.text());

        assertThat("requestTimestamp should be null when not provided",
            sentDto.getRequestTimestamp(), is(nullValue()));

        StreamFrameCallbackDispatcher.getInstance().reset();
    }

    // ---- tryWsDispatch sets breakpointId ----

    @Test
    public void tryWsDispatchShouldSetBreakpointIdFromMatcher() throws Exception {
        BreakpointMatcher matcherWithClientId = new BreakpointMatcher(
            "bp-try-ws", request().withPath("/test"),
            EnumSet.of(BreakpointPhase.RESPONSE_STREAM),
            null, CLIENT_ID
        );

        CompletableFuture<StreamFrameDecision> result = dispatcher.tryWsDispatch(
            matcherWithClientId, "stream-try-ws", 0,
            PausedStreamFrame.Direction.OUTBOUND, BreakpointPhase.RESPONSE_STREAM,
            "test".getBytes(), "GET", "/test", configuration, logger, webSocketClientRegistry
        );
        assertThat(result, is(notNullValue()));

        TextWebSocketFrame sentFrame = clientChannel.readOutbound();
        PausedStreamFrameDTO sentDto = (PausedStreamFrameDTO) serializer.deserialize(sentFrame.text());
        assertThat("tryWsDispatch should set breakpointId from the matcher",
            sentDto.getBreakpointId(), is("bp-try-ws"));

        // cleanup
        result.complete(StreamFrameDecision.continueFrame());
    }
}
