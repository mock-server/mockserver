package org.mockserver.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpMessage;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpRequestAndHttpResponse;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.WebSocketMessageSerializer;
import org.mockserver.serialization.model.PausedStreamFrameDTO;
import org.mockserver.serialization.model.StreamFrameDecisionDTO;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry.BREAKPOINT_ID_HEADER_NAME;
import static org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry.WEB_SOCKET_CORRELATION_ID_HEADER_NAME;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Tests the message routing logic in BreakpointWebSocketClient: given a
 * deserialized WS message, verify the correct handler is called and the reply
 * is correctly formatted.
 *
 * <p>Because BreakpointWebSocketClient.receivedTextWebSocketFrame is package-private,
 * this test lives in the same package and calls it directly, simulating what
 * the Netty handler would do.
 *
 * <p>Note: These tests verify the handler dispatch and reply serialization logic.
 * A full WS round-trip test requires a running server (integration test scope).
 */
public class BreakpointWebSocketClientTest {

    private static final MockServerLogger LOGGER = new MockServerLogger(BreakpointWebSocketClientTest.class);
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();
    private WebSocketMessageSerializer serializer;

    @Before
    public void setup() {
        serializer = new WebSocketMessageSerializer(LOGGER);
    }

    // -------------------------------------------------------------------
    // BreakpointRequestHandler contract tests
    // -------------------------------------------------------------------

    @Test
    public void requestHandlerReceivesCorrectRequest() {
        // given
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        BreakpointRequestHandler handler = req -> {
            captured.set(req);
            return req; // continue unchanged
        };

        // when
        HttpRequest request = request()
            .withMethod("POST")
            .withPath("/api/test")
            .withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, "corr-123");
        HttpMessage result = handler.handle(request);

        // then
        assertThat(captured.get(), is(notNullValue()));
        assertThat(captured.get().getMethod(""), is("POST"));
        assertThat(captured.get().getPath().getValue(), is("/api/test"));
        assertThat(result, instanceOf(HttpRequest.class));
    }

    @Test
    public void requestHandlerCanReturnResponseForAbort() {
        // given
        BreakpointRequestHandler handler = req ->
            response().withStatusCode(403).withBody("forbidden");

        // when
        HttpMessage result = handler.handle(request().withPath("/secret"));

        // then
        assertThat(result, instanceOf(HttpResponse.class));
        HttpResponse resp = (HttpResponse) result;
        assertThat(resp.getStatusCode(), is(403));
    }

    // -------------------------------------------------------------------
    // BreakpointResponseHandler contract tests
    // -------------------------------------------------------------------

    @Test
    public void responseHandlerReceivesRequestAndResponse() {
        // given
        AtomicReference<HttpRequest> capturedReq = new AtomicReference<>();
        AtomicReference<HttpResponse> capturedResp = new AtomicReference<>();
        BreakpointResponseHandler handler = (req, resp) -> {
            capturedReq.set(req);
            capturedResp.set(resp);
            // return a NEW response (the original is captured above for assertion)
            return response().withStatusCode(resp.getStatusCode()).withBody("modified");
        };

        // when
        HttpRequest request = request().withPath("/api/data");
        HttpResponse originalResponse = response().withStatusCode(200).withBody("original");
        HttpResponse result = handler.handle(request, originalResponse);

        // then
        assertThat(capturedReq.get().getPath().getValue(), is("/api/data"));
        assertThat(capturedResp.get().getBodyAsString(), is("original"));
        assertThat(result.getBodyAsString(), is("modified"));
        assertThat(result.getStatusCode(), is(200));
    }

    // -------------------------------------------------------------------
    // BreakpointStreamFrameHandler contract tests
    // -------------------------------------------------------------------

    @Test
    public void streamFrameHandlerContinue() {
        // given
        BreakpointStreamFrameHandler handler = frame ->
            new StreamFrameDecisionDTO()
                .setCorrelationId(frame.getCorrelationId())
                .setAction("CONTINUE");

        PausedStreamFrameDTO frame = new PausedStreamFrameDTO()
            .setCorrelationId("frame-corr-1")
            .setStreamId("stream-1")
            .setSequenceNumber(0)
            .setDirection("OUTBOUND")
            .setPhase("RESPONSE_STREAM")
            .setBody(Base64.getEncoder().encodeToString("hello".getBytes()));

        // when
        StreamFrameDecisionDTO decision = handler.handle(frame);

        // then
        assertThat(decision.getCorrelationId(), is("frame-corr-1"));
        assertThat(decision.getAction(), is("CONTINUE"));
        assertThat(decision.getBody(), is(nullValue()));
    }

    @Test
    public void streamFrameHandlerModify() {
        // given
        BreakpointStreamFrameHandler handler = frame -> {
            byte[] original = Base64.getDecoder().decode(frame.getBody());
            byte[] modified = ("modified-" + new String(original)).getBytes();
            return new StreamFrameDecisionDTO()
                .setCorrelationId(frame.getCorrelationId())
                .setAction("MODIFY")
                .setBody(Base64.getEncoder().encodeToString(modified));
        };

        PausedStreamFrameDTO frame = new PausedStreamFrameDTO()
            .setCorrelationId("frame-corr-2")
            .setStreamId("stream-1")
            .setSequenceNumber(1)
            .setDirection("OUTBOUND")
            .setPhase("RESPONSE_STREAM")
            .setBody(Base64.getEncoder().encodeToString("data".getBytes()));

        // when
        StreamFrameDecisionDTO decision = handler.handle(frame);

        // then
        assertThat(decision.getCorrelationId(), is("frame-corr-2"));
        assertThat(decision.getAction(), is("MODIFY"));
        byte[] decoded = Base64.getDecoder().decode(decision.getBody());
        assertThat(new String(decoded), is("modified-data"));
    }

    @Test
    public void streamFrameHandlerDrop() {
        // given
        BreakpointStreamFrameHandler handler = frame ->
            new StreamFrameDecisionDTO()
                .setCorrelationId(frame.getCorrelationId())
                .setAction("DROP");

        PausedStreamFrameDTO frame = new PausedStreamFrameDTO()
            .setCorrelationId("frame-corr-3")
            .setStreamId("stream-2")
            .setSequenceNumber(0)
            .setDirection("INBOUND")
            .setPhase("INBOUND_STREAM")
            .setBody(Base64.getEncoder().encodeToString("inbound-data".getBytes()));

        // when
        StreamFrameDecisionDTO decision = handler.handle(frame);

        // then
        assertThat(decision.getCorrelationId(), is("frame-corr-3"));
        assertThat(decision.getAction(), is("DROP"));
    }

    @Test
    public void streamFrameHandlerInject() {
        // given
        byte[] injectedBytes = "injected-content".getBytes();
        BreakpointStreamFrameHandler handler = frame ->
            new StreamFrameDecisionDTO()
                .setCorrelationId(frame.getCorrelationId())
                .setAction("INJECT")
                .setBody(Base64.getEncoder().encodeToString(injectedBytes));

        PausedStreamFrameDTO frame = new PausedStreamFrameDTO()
            .setCorrelationId("frame-corr-4")
            .setStreamId("stream-3")
            .setSequenceNumber(0)
            .setDirection("OUTBOUND")
            .setPhase("RESPONSE_STREAM")
            .setBody(Base64.getEncoder().encodeToString("original".getBytes()));

        // when
        StreamFrameDecisionDTO decision = handler.handle(frame);

        // then
        assertThat(decision.getCorrelationId(), is("frame-corr-4"));
        assertThat(decision.getAction(), is("INJECT"));
        byte[] decoded = Base64.getDecoder().decode(decision.getBody());
        assertThat(new String(decoded), is("injected-content"));
    }

    @Test
    public void streamFrameHandlerClose() {
        // given
        BreakpointStreamFrameHandler handler = frame ->
            new StreamFrameDecisionDTO()
                .setCorrelationId(frame.getCorrelationId())
                .setAction("CLOSE");

        PausedStreamFrameDTO frame = new PausedStreamFrameDTO()
            .setCorrelationId("frame-corr-5")
            .setStreamId("stream-4")
            .setSequenceNumber(2)
            .setDirection("OUTBOUND")
            .setPhase("RESPONSE_STREAM")
            .setBody(Base64.getEncoder().encodeToString("closing".getBytes()));

        // when
        StreamFrameDecisionDTO decision = handler.handle(frame);

        // then
        assertThat(decision.getCorrelationId(), is("frame-corr-5"));
        assertThat(decision.getAction(), is("CLOSE"));
    }

    // -------------------------------------------------------------------
    // PausedStreamFrameDTO / StreamFrameDecisionDTO serialization
    // -------------------------------------------------------------------

    @Test
    public void pausedStreamFrameDTOSerializesCorrectly() throws Exception {
        // given
        PausedStreamFrameDTO dto = new PausedStreamFrameDTO()
            .setCorrelationId("test-corr")
            .setStreamId("stream-1")
            .setSequenceNumber(5)
            .setDirection("OUTBOUND")
            .setPhase("RESPONSE_STREAM")
            .setBody(Base64.getEncoder().encodeToString("test-body".getBytes()))
            .setRequestMethod("GET")
            .setRequestPath("/api/test");

        // when
        String serialized = serializer.serialize(dto);

        // then -- verify it can be deserialized back
        Object deserialized = serializer.deserialize(serialized);
        assertThat(deserialized, instanceOf(PausedStreamFrameDTO.class));
        PausedStreamFrameDTO result = (PausedStreamFrameDTO) deserialized;
        assertThat(result.getCorrelationId(), is("test-corr"));
        assertThat(result.getStreamId(), is("stream-1"));
        assertThat(result.getSequenceNumber(), is(5));
        assertThat(result.getDirection(), is("OUTBOUND"));
        assertThat(result.getPhase(), is("RESPONSE_STREAM"));
        assertThat(new String(Base64.getDecoder().decode(result.getBody())), is("test-body"));
        assertThat(result.getRequestMethod(), is("GET"));
        assertThat(result.getRequestPath(), is("/api/test"));
    }

    @Test
    public void streamFrameDecisionDTOSerializesCorrectly() throws Exception {
        // given
        StreamFrameDecisionDTO dto = new StreamFrameDecisionDTO()
            .setCorrelationId("test-corr")
            .setAction("MODIFY")
            .setBody(Base64.getEncoder().encodeToString("replacement".getBytes()));

        // when
        String serialized = serializer.serialize(dto);

        // then -- verify it can be deserialized back
        Object deserialized = serializer.deserialize(serialized);
        assertThat(deserialized, instanceOf(StreamFrameDecisionDTO.class));
        StreamFrameDecisionDTO result = (StreamFrameDecisionDTO) deserialized;
        assertThat(result.getCorrelationId(), is("test-corr"));
        assertThat(result.getAction(), is("MODIFY"));
        assertThat(new String(Base64.getDecoder().decode(result.getBody())), is("replacement"));
    }

    // -------------------------------------------------------------------
    // WebSocketMessageSerializer round-trip for frame DTOs
    // -------------------------------------------------------------------

    @Test
    public void webSocketMessageSerializerHandlesAllFrameActions() throws Exception {
        // Test each action type through the serializer
        String[] actions = {"CONTINUE", "MODIFY", "DROP", "INJECT", "CLOSE"};
        for (String action : actions) {
            StreamFrameDecisionDTO dto = new StreamFrameDecisionDTO()
                .setCorrelationId("corr-" + action)
                .setAction(action);
            if ("MODIFY".equals(action) || "INJECT".equals(action)) {
                dto.setBody(Base64.getEncoder().encodeToString(("body-" + action).getBytes()));
            }

            String serialized = serializer.serialize(dto);
            Object deserialized = serializer.deserialize(serialized);
            assertThat("action " + action + " should round-trip",
                deserialized, instanceOf(StreamFrameDecisionDTO.class));
            StreamFrameDecisionDTO result = (StreamFrameDecisionDTO) deserialized;
            assertThat(result.getAction(), is(action));
            assertThat(result.getCorrelationId(), is("corr-" + action));
        }
    }

    @Test
    public void pausedStreamFrameDTOWithInboundDirection() throws Exception {
        PausedStreamFrameDTO dto = new PausedStreamFrameDTO()
            .setCorrelationId("inbound-corr")
            .setStreamId("bidi-stream-1")
            .setSequenceNumber(0)
            .setDirection("INBOUND")
            .setPhase("INBOUND_STREAM")
            .setBody(Base64.getEncoder().encodeToString("client-msg".getBytes()));

        String serialized = serializer.serialize(dto);
        Object deserialized = serializer.deserialize(serialized);
        assertThat(deserialized, instanceOf(PausedStreamFrameDTO.class));
        PausedStreamFrameDTO result = (PausedStreamFrameDTO) deserialized;
        assertThat(result.getDirection(), is("INBOUND"));
        assertThat(result.getPhase(), is("INBOUND_STREAM"));
    }

    @Test
    public void pausedStreamFrameDTOWithBreakpointIdRoundTrips() throws Exception {
        PausedStreamFrameDTO dto = new PausedStreamFrameDTO()
            .setCorrelationId("bp-corr")
            .setStreamId("stream-1")
            .setSequenceNumber(0)
            .setDirection("OUTBOUND")
            .setPhase("RESPONSE_STREAM")
            .setBody(Base64.getEncoder().encodeToString("data".getBytes()))
            .setBreakpointId("bp-uuid-123");

        String serialized = serializer.serialize(dto);
        Object deserialized = serializer.deserialize(serialized);
        assertThat(deserialized, instanceOf(PausedStreamFrameDTO.class));
        PausedStreamFrameDTO result = (PausedStreamFrameDTO) deserialized;
        assertThat(result.getBreakpointId(), is("bp-uuid-123"));
    }

    // -------------------------------------------------------------------
    // MINOR H: Per-breakpoint-id routing tests
    // These exercise receivedTextWebSocketFrame directly with an
    // EmbeddedChannel injected via reflection, verifying:
    //   - correct handler invoked by breakpoint id
    //   - correlationId echoed in the reply
    //   - auto-continue when no/unknown handler
    //   - frame round-trip
    // -------------------------------------------------------------------

    /**
     * Create a BreakpointWebSocketClient with an EmbeddedChannel injected
     * so we can call receivedTextWebSocketFrame and read outbound replies.
     */
    private BreakpointWebSocketClient createClientWithChannel(EmbeddedChannel embeddedChannel) {
        BreakpointWebSocketClient client = new BreakpointWebSocketClient(
            embeddedChannel.eventLoop().parent(),
            "test-client-id",
            LOGGER,
            () -> {
                throw new AssertionError("no TLS connection is made");
            }
        );
        // Inject the channel via reflection since the field is package-private
        try {
            Field channelField = BreakpointWebSocketClient.class.getDeclaredField("channel");
            channelField.setAccessible(true);
            channelField.set(client, embeddedChannel);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject channel", e);
        }
        return client;
    }

    @Test
    public void requestPhaseRoutesToCorrectHandlerByBreakpointId() throws Exception {
        // given
        EmbeddedChannel ch = new EmbeddedChannel();
        BreakpointWebSocketClient client = createClientWithChannel(ch);

        AtomicReference<HttpRequest> capturedByBp1 = new AtomicReference<>();
        AtomicReference<HttpRequest> capturedByBp2 = new AtomicReference<>();

        client.setRequestHandler("bp-1", req -> {
            capturedByBp1.set(req);
            return request().withMethod("MODIFIED-BY-BP1").withPath(req.getPath().getValue());
        });
        client.setResponseHandler("bp-1", (req, resp) -> response().withStatusCode(201));

        client.setRequestHandler("bp-2", req -> {
            capturedByBp2.set(req);
            return request().withMethod("MODIFIED-BY-BP2").withPath(req.getPath().getValue());
        });

        // when -- send a message tagged with bp-1
        HttpRequest incomingForBp1 = request().withMethod("GET").withPath("/api/a")
            .withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, "corr-aaa")
            .withHeader(BREAKPOINT_ID_HEADER_NAME, "bp-1");
        String json1 = serializer.serialize(incomingForBp1);
        client.receivedTextWebSocketFrame(new TextWebSocketFrame(json1));

        // then -- bp-1 handler was called, bp-2 was not
        assertThat(capturedByBp1.get(), is(notNullValue()));
        assertThat(capturedByBp1.get().getPath().getValue(), is("/api/a"));
        assertThat(capturedByBp2.get(), is(nullValue()));

        // verify reply has correlationId
        TextWebSocketFrame reply1 = ch.readOutbound();
        assertThat(reply1, is(notNullValue()));
        Object replyMessage1 = serializer.deserialize(reply1.text());
        assertThat(replyMessage1, instanceOf(HttpRequest.class));
        HttpRequest replyReq1 = (HttpRequest) replyMessage1;
        assertThat(replyReq1.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME), is("corr-aaa"));
        assertThat(replyReq1.getMethod(""), is("MODIFIED-BY-BP1"));

        // when -- send a message tagged with bp-2
        HttpRequest incomingForBp2 = request().withMethod("POST").withPath("/api/b")
            .withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, "corr-bbb")
            .withHeader(BREAKPOINT_ID_HEADER_NAME, "bp-2");
        String json2 = serializer.serialize(incomingForBp2);
        client.receivedTextWebSocketFrame(new TextWebSocketFrame(json2));

        // then -- bp-2 handler was called
        assertThat(capturedByBp2.get(), is(notNullValue()));
        assertThat(capturedByBp2.get().getPath().getValue(), is("/api/b"));

        TextWebSocketFrame reply2 = ch.readOutbound();
        assertThat(reply2, is(notNullValue()));
        Object replyMessage2 = serializer.deserialize(reply2.text());
        assertThat(replyMessage2, instanceOf(HttpRequest.class));
        HttpRequest replyReq2 = (HttpRequest) replyMessage2;
        assertThat(replyReq2.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME), is("corr-bbb"));
        assertThat(replyReq2.getMethod(""), is("MODIFIED-BY-BP2"));

        ch.close();
    }

    @Test
    public void requestPhaseAutoContinuesWhenBreakpointIdUnknown() throws Exception {
        // given
        EmbeddedChannel ch = new EmbeddedChannel();
        BreakpointWebSocketClient client = createClientWithChannel(ch);

        // register a handler for bp-known but send a message for bp-unknown
        client.setRequestHandler("bp-known", req ->
            request().withMethod("SHOULD-NOT-BE-CALLED"));

        // when
        HttpRequest incoming = request().withMethod("GET").withPath("/api/unknown")
            .withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, "corr-xyz")
            .withHeader(BREAKPOINT_ID_HEADER_NAME, "bp-unknown");
        client.receivedTextWebSocketFrame(new TextWebSocketFrame(serializer.serialize(incoming)));

        // then -- auto-continue: reply is the original request with correlationId
        TextWebSocketFrame reply = ch.readOutbound();
        assertThat(reply, is(notNullValue()));
        Object replyMessage = serializer.deserialize(reply.text());
        assertThat(replyMessage, instanceOf(HttpRequest.class));
        HttpRequest replyReq = (HttpRequest) replyMessage;
        assertThat(replyReq.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME), is("corr-xyz"));
        assertThat(replyReq.getMethod(""), is("GET"));
        assertThat(replyReq.getPath().getValue(), is("/api/unknown"));

        ch.close();
    }

    @Test
    public void requestPhaseAutoContinuesWhenBreakpointIdAbsent() throws Exception {
        // given
        EmbeddedChannel ch = new EmbeddedChannel();
        BreakpointWebSocketClient client = createClientWithChannel(ch);

        client.setRequestHandler("bp-1", req ->
            request().withMethod("SHOULD-NOT-BE-CALLED"));

        // when -- no breakpointId header
        HttpRequest incoming = request().withMethod("GET").withPath("/api/no-bp-id")
            .withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, "corr-noid");
        client.receivedTextWebSocketFrame(new TextWebSocketFrame(serializer.serialize(incoming)));

        // then -- auto-continue
        TextWebSocketFrame reply = ch.readOutbound();
        assertThat(reply, is(notNullValue()));
        Object replyMessage = serializer.deserialize(reply.text());
        assertThat(replyMessage, instanceOf(HttpRequest.class));
        HttpRequest replyReq = (HttpRequest) replyMessage;
        assertThat(replyReq.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME), is("corr-noid"));
        assertThat(replyReq.getMethod(""), is("GET"));

        ch.close();
    }

    @Test
    public void responsePhaseRoutesToCorrectHandler() throws Exception {
        // given
        EmbeddedChannel ch = new EmbeddedChannel();
        BreakpointWebSocketClient client = createClientWithChannel(ch);

        AtomicReference<HttpResponse> capturedResp = new AtomicReference<>();
        client.setResponseHandler("bp-resp-1", (req, resp) -> {
            capturedResp.set(resp);
            return response().withStatusCode(201).withBody("modified-by-bp-resp-1");
        });

        // when
        HttpRequest reqPart = request().withMethod("GET").withPath("/api/resp")
            .withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, "corr-resp-1")
            .withHeader(BREAKPOINT_ID_HEADER_NAME, "bp-resp-1");
        HttpResponse respPart = response().withStatusCode(200).withBody("original");
        HttpRequestAndHttpResponse pair = new HttpRequestAndHttpResponse()
            .withHttpRequest(reqPart)
            .withHttpResponse(respPart);

        client.receivedTextWebSocketFrame(new TextWebSocketFrame(serializer.serialize(pair)));

        // then
        assertThat(capturedResp.get(), is(notNullValue()));
        assertThat(capturedResp.get().getStatusCode(), is(200));

        TextWebSocketFrame reply = ch.readOutbound();
        assertThat(reply, is(notNullValue()));
        Object replyMessage = serializer.deserialize(reply.text());
        assertThat(replyMessage, instanceOf(HttpResponse.class));
        HttpResponse replyResp = (HttpResponse) replyMessage;
        assertThat(replyResp.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME), is("corr-resp-1"));
        assertThat(replyResp.getStatusCode(), is(201));

        ch.close();
    }

    @Test
    public void responsePhaseAutoContinuesWhenNoHandler() throws Exception {
        // given
        EmbeddedChannel ch = new EmbeddedChannel();
        BreakpointWebSocketClient client = createClientWithChannel(ch);

        // when -- no handler registered for bp-resp-missing
        HttpRequest reqPart = request().withMethod("GET").withPath("/api/resp")
            .withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, "corr-resp-miss")
            .withHeader(BREAKPOINT_ID_HEADER_NAME, "bp-resp-missing");
        HttpResponse respPart = response().withStatusCode(200).withBody("original-resp");
        HttpRequestAndHttpResponse pair = new HttpRequestAndHttpResponse()
            .withHttpRequest(reqPart)
            .withHttpResponse(respPart);

        client.receivedTextWebSocketFrame(new TextWebSocketFrame(serializer.serialize(pair)));

        // then -- auto-continue with original response
        TextWebSocketFrame reply = ch.readOutbound();
        assertThat(reply, is(notNullValue()));
        Object replyMessage = serializer.deserialize(reply.text());
        assertThat(replyMessage, instanceOf(HttpResponse.class));
        HttpResponse replyResp = (HttpResponse) replyMessage;
        assertThat(replyResp.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME), is("corr-resp-miss"));
        assertThat(replyResp.getStatusCode(), is(200));

        ch.close();
    }

    @Test
    public void streamFramePhaseRoutesToCorrectHandler() throws Exception {
        // given
        EmbeddedChannel ch = new EmbeddedChannel();
        BreakpointWebSocketClient client = createClientWithChannel(ch);

        AtomicReference<PausedStreamFrameDTO> capturedFrame = new AtomicReference<>();
        client.setStreamFrameHandler("bp-stream-1", frame -> {
            capturedFrame.set(frame);
            return new StreamFrameDecisionDTO()
                .setCorrelationId(frame.getCorrelationId())
                .setAction("MODIFY")
                .setBody(Base64.getEncoder().encodeToString("modified-stream".getBytes()));
        });

        // when
        PausedStreamFrameDTO dto = new PausedStreamFrameDTO()
            .setCorrelationId("frame-corr-routing")
            .setStreamId("stream-99")
            .setSequenceNumber(0)
            .setDirection("OUTBOUND")
            .setPhase("RESPONSE_STREAM")
            .setBody(Base64.getEncoder().encodeToString("original-stream".getBytes()))
            .setBreakpointId("bp-stream-1");

        client.receivedTextWebSocketFrame(new TextWebSocketFrame(serializer.serialize(dto)));

        // then
        assertThat(capturedFrame.get(), is(notNullValue()));
        assertThat(capturedFrame.get().getStreamId(), is("stream-99"));

        TextWebSocketFrame reply = ch.readOutbound();
        assertThat(reply, is(notNullValue()));
        Object replyMessage = serializer.deserialize(reply.text());
        assertThat(replyMessage, instanceOf(StreamFrameDecisionDTO.class));
        StreamFrameDecisionDTO replyDecision = (StreamFrameDecisionDTO) replyMessage;
        assertThat(replyDecision.getCorrelationId(), is("frame-corr-routing"));
        assertThat(replyDecision.getAction(), is("MODIFY"));

        ch.close();
    }

    @Test
    public void streamFrameAutoContinuesWhenNoHandler() throws Exception {
        // given
        EmbeddedChannel ch = new EmbeddedChannel();
        BreakpointWebSocketClient client = createClientWithChannel(ch);

        // when -- no handler for bp-stream-missing
        PausedStreamFrameDTO dto = new PausedStreamFrameDTO()
            .setCorrelationId("frame-corr-miss")
            .setStreamId("stream-99")
            .setSequenceNumber(0)
            .setDirection("OUTBOUND")
            .setPhase("RESPONSE_STREAM")
            .setBody(Base64.getEncoder().encodeToString("data".getBytes()))
            .setBreakpointId("bp-stream-missing");

        client.receivedTextWebSocketFrame(new TextWebSocketFrame(serializer.serialize(dto)));

        // then -- auto-continue
        TextWebSocketFrame reply = ch.readOutbound();
        assertThat(reply, is(notNullValue()));
        Object replyMessage = serializer.deserialize(reply.text());
        assertThat(replyMessage, instanceOf(StreamFrameDecisionDTO.class));
        StreamFrameDecisionDTO replyDecision = (StreamFrameDecisionDTO) replyMessage;
        assertThat(replyDecision.getCorrelationId(), is("frame-corr-miss"));
        assertThat(replyDecision.getAction(), is("CONTINUE"));

        ch.close();
    }

    @Test
    public void requestHandlerReturningNullAutoContinues() throws Exception {
        // given -- MINOR F: null handler return
        EmbeddedChannel ch = new EmbeddedChannel();
        BreakpointWebSocketClient client = createClientWithChannel(ch);

        client.setRequestHandler("bp-null-req", req -> null);

        // when
        HttpRequest incoming = request().withMethod("GET").withPath("/api/null-ret")
            .withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, "corr-null-req")
            .withHeader(BREAKPOINT_ID_HEADER_NAME, "bp-null-req");
        client.receivedTextWebSocketFrame(new TextWebSocketFrame(serializer.serialize(incoming)));

        // then -- auto-continue with original request
        TextWebSocketFrame reply = ch.readOutbound();
        assertThat(reply, is(notNullValue()));
        Object replyMessage = serializer.deserialize(reply.text());
        assertThat(replyMessage, instanceOf(HttpRequest.class));
        HttpRequest replyReq = (HttpRequest) replyMessage;
        assertThat(replyReq.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME), is("corr-null-req"));
        assertThat(replyReq.getPath().getValue(), is("/api/null-ret"));

        ch.close();
    }

    @Test
    public void responseHandlerReturningNullAutoContinues() throws Exception {
        // given -- MINOR F: null handler return
        EmbeddedChannel ch = new EmbeddedChannel();
        BreakpointWebSocketClient client = createClientWithChannel(ch);

        client.setResponseHandler("bp-null-resp", (req, resp) -> null);

        // when
        HttpRequest reqPart = request().withMethod("GET").withPath("/api/null-resp-ret")
            .withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, "corr-null-resp")
            .withHeader(BREAKPOINT_ID_HEADER_NAME, "bp-null-resp");
        HttpResponse respPart = response().withStatusCode(200).withBody("original");
        HttpRequestAndHttpResponse pair = new HttpRequestAndHttpResponse()
            .withHttpRequest(reqPart)
            .withHttpResponse(respPart);

        client.receivedTextWebSocketFrame(new TextWebSocketFrame(serializer.serialize(pair)));

        // then -- auto-continue with original response
        TextWebSocketFrame reply = ch.readOutbound();
        assertThat(reply, is(notNullValue()));
        Object replyMessage = serializer.deserialize(reply.text());
        assertThat(replyMessage, instanceOf(HttpResponse.class));
        HttpResponse replyResp = (HttpResponse) replyMessage;
        assertThat(replyResp.getFirstHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME), is("corr-null-resp"));
        assertThat(replyResp.getStatusCode(), is(200));

        ch.close();
    }

    @Test
    public void streamFrameHandlerReturningNullAutoContinues() throws Exception {
        // given -- MINOR F: null handler return
        EmbeddedChannel ch = new EmbeddedChannel();
        BreakpointWebSocketClient client = createClientWithChannel(ch);

        client.setStreamFrameHandler("bp-null-frame", frame -> null);

        // when
        PausedStreamFrameDTO dto = new PausedStreamFrameDTO()
            .setCorrelationId("frame-corr-null")
            .setStreamId("stream-null")
            .setSequenceNumber(0)
            .setDirection("OUTBOUND")
            .setPhase("RESPONSE_STREAM")
            .setBody(Base64.getEncoder().encodeToString("data".getBytes()))
            .setBreakpointId("bp-null-frame");

        client.receivedTextWebSocketFrame(new TextWebSocketFrame(serializer.serialize(dto)));

        // then -- auto-continue
        TextWebSocketFrame reply = ch.readOutbound();
        assertThat(reply, is(notNullValue()));
        Object replyMessage = serializer.deserialize(reply.text());
        assertThat(replyMessage, instanceOf(StreamFrameDecisionDTO.class));
        StreamFrameDecisionDTO replyDecision = (StreamFrameDecisionDTO) replyMessage;
        assertThat(replyDecision.getCorrelationId(), is("frame-corr-null"));
        assertThat(replyDecision.getAction(), is("CONTINUE"));

        ch.close();
    }

    @Test
    public void removeHandlersPreventsRouting() throws Exception {
        // given
        EmbeddedChannel ch = new EmbeddedChannel();
        BreakpointWebSocketClient client = createClientWithChannel(ch);

        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        client.setRequestHandler("bp-remove-me", req -> {
            captured.set(req);
            return request().withMethod("HANDLED");
        });

        // remove the handler
        client.removeHandlers("bp-remove-me");

        // when
        HttpRequest incoming = request().withMethod("GET").withPath("/api/removed")
            .withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, "corr-removed")
            .withHeader(BREAKPOINT_ID_HEADER_NAME, "bp-remove-me");
        client.receivedTextWebSocketFrame(new TextWebSocketFrame(serializer.serialize(incoming)));

        // then -- handler was not called; auto-continue instead
        assertThat(captured.get(), is(nullValue()));
        TextWebSocketFrame reply = ch.readOutbound();
        assertThat(reply, is(notNullValue()));
        Object replyMessage = serializer.deserialize(reply.text());
        assertThat(replyMessage, instanceOf(HttpRequest.class));
        HttpRequest replyReq = (HttpRequest) replyMessage;
        assertThat(replyReq.getMethod(""), is("GET")); // original, not HANDLED

        ch.close();
    }

    @Test
    public void clearHandlersPreventsAllRouting() throws Exception {
        // given
        EmbeddedChannel ch = new EmbeddedChannel();
        BreakpointWebSocketClient client = createClientWithChannel(ch);

        client.setRequestHandler("bp-clear-1", req -> request().withMethod("HANDLED"));
        client.setRequestHandler("bp-clear-2", req -> request().withMethod("HANDLED"));

        // clear all
        client.clearHandlers();

        // when
        HttpRequest incoming = request().withMethod("GET").withPath("/api/cleared")
            .withHeader(WEB_SOCKET_CORRELATION_ID_HEADER_NAME, "corr-cleared")
            .withHeader(BREAKPOINT_ID_HEADER_NAME, "bp-clear-1");
        client.receivedTextWebSocketFrame(new TextWebSocketFrame(serializer.serialize(incoming)));

        // then -- auto-continue
        TextWebSocketFrame reply = ch.readOutbound();
        assertThat(reply, is(notNullValue()));
        Object replyMessage = serializer.deserialize(reply.text());
        assertThat(replyMessage, instanceOf(HttpRequest.class));
        HttpRequest replyReq = (HttpRequest) replyMessage;
        assertThat(replyReq.getMethod(""), is("GET")); // original

        ch.close();
    }

    @Test
    public void handshakeUsesTheBareCallbackUriAndAsksForReleasedNoticesWithAHeader() throws Exception {
        // servers up to 8.0.0 open the callback WebSocket only when the URI is exactly the bare path
        EmbeddedChannel ch = new EmbeddedChannel(new io.netty.handler.codec.http.HttpClientCodec());
        ch.pipeline().addLast(new BreakpointWebSocketClient.BreakpointWebSocketClientHandler(
            LOGGER, "test-client-id", new java.net.InetSocketAddress("localhost", 1080), "", null, false));
        ch.pipeline().fireChannelActive();

        StringBuilder written = new StringBuilder();
        Object outbound;
        while ((outbound = ch.readOutbound()) != null) {
            written.append(((io.netty.buffer.ByteBuf) outbound).toString(java.nio.charset.StandardCharsets.US_ASCII));
            ((io.netty.buffer.ByteBuf) outbound).release();
        }
        String[] lines = written.toString().split("\r\n");
        assertThat(lines[0], is("GET /_mockserver_callback_websocket HTTP/1.1"));
        assertThat(java.util.Arrays.stream(lines).anyMatch(line -> line.equalsIgnoreCase("X-MockServer-Capabilities: breakpointReleased")), is(true));
        assertThat(java.util.Arrays.stream(lines).anyMatch(line -> line.equalsIgnoreCase("X-CLIENT-REGISTRATION-ID: test-client-id")), is(true));
        ch.finishAndReleaseAll();
    }

    @Test
    public void handshakeSendsNoOriginHeaderBecauseItIsNotABrowser() throws Exception {
        assertThat(handshakeLines(null).stream().anyMatch(line -> line.toLowerCase().startsWith("origin:")), is(false));
    }

    @Test
    public void handshakeSendsTheAuthorizationHeaderWhenOneIsConfigured() throws Exception {
        assertThat(handshakeLines("Bearer some.jwt.value"), hasItem("Authorization: Bearer some.jwt.value"));
        assertThat(handshakeLines(null).stream().anyMatch(line -> line.toLowerCase().startsWith("authorization:")), is(false));
        assertThat(handshakeLines(" ").stream().anyMatch(line -> line.toLowerCase().startsWith("authorization:")), is(false));
    }

    @Test
    public void answersAPingAtOnceSoMockServerSeesTheConnectionIsAlive() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new BreakpointWebSocketClient.BreakpointWebSocketClientHandler(
            LOGGER, "test-client-id", new java.net.InetSocketAddress("localhost", 1080), "", null, false));
        Object handshake;
        while ((handshake = ch.readOutbound()) != null) {
            io.netty.util.ReferenceCountUtil.release(handshake);
        }

        ch.writeInbound(new io.netty.handler.codec.http.websocketx.PingWebSocketFrame(io.netty.buffer.Unpooled.copiedBuffer("liveness", java.nio.charset.StandardCharsets.UTF_8)));

        Object pong = ch.readOutbound();
        assertThat(pong, instanceOf(io.netty.handler.codec.http.websocketx.PongWebSocketFrame.class));
        assertThat(((io.netty.handler.codec.http.websocketx.PongWebSocketFrame) pong).content().toString(java.nio.charset.StandardCharsets.UTF_8), is("liveness"));
        io.netty.util.ReferenceCountUtil.release(pong);
        ch.finishAndReleaseAll();
    }

    private static java.util.List<String> handshakeLines(String authorization) throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new io.netty.handler.codec.http.HttpClientCodec());
        ch.pipeline().addLast(new BreakpointWebSocketClient.BreakpointWebSocketClientHandler(
            LOGGER, "test-client-id", new java.net.InetSocketAddress("localhost", 1080), "", null, false, authorization));
        ch.pipeline().fireChannelActive();
        StringBuilder written = new StringBuilder();
        Object outbound;
        while ((outbound = ch.readOutbound()) != null) {
            written.append(((io.netty.buffer.ByteBuf) outbound).toString(java.nio.charset.StandardCharsets.US_ASCII));
            ((io.netty.buffer.ByteBuf) outbound).release();
        }
        ch.finishAndReleaseAll();
        return java.util.Arrays.asList(written.toString().split("\r\n"));
    }

    @Test
    public void aMessageTypeFromANewerServerIsIgnoredWithoutAReplyOrAnError() throws Exception {
        MockServerLogger logger = org.mockito.Mockito.mock(MockServerLogger.class);
        org.mockito.Mockito.when(logger.isEnabledForInstance(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        EmbeddedChannel ch = new EmbeddedChannel();
        BreakpointWebSocketClient client = new BreakpointWebSocketClient(
            ch.eventLoop().parent(), "test-client-id", logger, () -> {
                throw new AssertionError("no TLS connection is made");
            });
        Field channelField = BreakpointWebSocketClient.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        channelField.set(client, ch);

        client.receivedTextWebSocketFrame(new TextWebSocketFrame(
            "{\"type\":\"org.mockserver.serialization.model.SomeFutureNoticeDTO\",\"value\":\"{\\\"correlationId\\\":\\\"c\\\"}\"}"));

        assertThat(ch.readOutbound(), is(nullValue()));
        org.mockito.Mockito.verify(logger, org.mockito.Mockito.never()).logEvent(org.mockito.ArgumentMatchers.argThat(
            entry -> entry.getLogLevel() == org.slf4j.event.Level.WARN || entry.getLogLevel() == org.slf4j.event.Level.ERROR));
        org.mockito.Mockito.verify(logger).logEvent(org.mockito.ArgumentMatchers.argThat(
            entry -> entry.getLogLevel() == org.slf4j.event.Level.DEBUG));
        ch.close();
    }

    @Test
    public void releasedNoticeNeedsNoReplyAndIsNotReportedAsUnsupported() throws Exception {
        // given -- a logger that records every event it is asked to log
        MockServerLogger logger = org.mockito.Mockito.mock(MockServerLogger.class);
        org.mockito.Mockito.when(logger.isEnabledForInstance(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        EmbeddedChannel ch = new EmbeddedChannel();
        BreakpointWebSocketClient client = new BreakpointWebSocketClient(
            ch.eventLoop().parent(), "test-client-id", logger, () -> {
                throw new AssertionError("no TLS connection is made");
            });
        Field channelField = BreakpointWebSocketClient.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        channelField.set(client, ch);

        // when -- the server says it continued a paused request itself
        client.receivedTextWebSocketFrame(new TextWebSocketFrame(serializer.serialize(
            new org.mockserver.serialization.model.BreakpointReleasedDTO()
                .setCorrelationId("corr-released")
                .setReason(org.mockserver.serialization.model.BreakpointReleasedDTO.REASON_TIMEOUT))));

        // then -- nothing is sent back and nothing is logged as a warning or error
        assertThat(ch.readOutbound(), is(nullValue()));
        org.mockito.Mockito.verify(logger, org.mockito.Mockito.never()).logEvent(org.mockito.ArgumentMatchers.argThat(
            entry -> entry.getLogLevel() == org.slf4j.event.Level.WARN || entry.getLogLevel() == org.slf4j.event.Level.ERROR));
        ch.close();
    }
}
