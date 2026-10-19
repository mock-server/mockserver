package org.mockserver.netty.http3;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.grpc.GrpcFrameCodec;
import org.mockserver.grpc.GrpcJsonMessageConverter;
import org.mockserver.grpc.GrpcProtoDescriptorStore;
import org.mockserver.grpc.GrpcStatusMapper;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.breakpoint.BreakpointPhase;
import org.mockserver.model.GrpcBidiResponse;
import org.mockserver.model.GrpcBidiRule;
import org.mockserver.model.GrpcStreamResponse;
import org.mockserver.netty.MockServer;
import org.mockserver.serialization.model.PausedStreamFrameDTO;
import org.mockserver.serialization.model.StreamFrameDecisionDTO;
import org.mockserver.testing.socket.Ipv4DatagramChannelFactory;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.netty.http3.Http3TestServer.startWithHttp3;

/**
 * Integration tests for gRPC <strong>streaming</strong> over HTTP/3 -- the two cases
 * that were previously deferred (G16-FOLLOW-UP-5):
 * <ul>
 *   <li><strong>server-streaming</strong> ({@code rpc ListGreetings (HelloRequest) returns
 *       (stream HelloResponse)}): a unary request yields multiple response DATA frames followed
 *       by a trailing HEADERS frame with grpc-status;</li>
 *   <li><strong>bidi-streaming</strong> ({@code rpc Chat (stream HelloRequest) returns
 *       (stream HelloResponse)}): multiple request DATA frames are matched against
 *       {@link GrpcBidiRule}s and produce interleaved response DATA frames, then a trailing
 *       HEADERS frame on FIN.</li>
 * </ul>
 * These use an in-JVM Netty QUIC client and skip gracefully when native QUIC is unavailable.
 */
@SuppressWarnings("deprecation") // NioEventLoopGroup deprecation in Netty 4.2
public class Http3GrpcStreamingIntegrationTest {

    private static final Path DESCRIPTOR_DIR = Paths.get("../mockserver-core/src/test/resources/grpc").toAbsolutePath();
    private static final String SERVICE = "com.example.grpc.GreetingService";

    private MockServer mockServer;
    private MockServerClient mockServerClient;
    private NioEventLoopGroup clientGroup;
    private GrpcProtoDescriptorStore descriptorStore;
    private GrpcJsonMessageConverter converter;

    @Before
    public void setUp() {
        assumeQuicAvailable();
        descriptorStore = new GrpcProtoDescriptorStore(new MockServerLogger());
        descriptorStore.loadDescriptorSetFromPath(DESCRIPTOR_DIR.resolve("greeting.dsc"));
        converter = descriptorStore.getConverter();
    }

    @After
    public void tearDown() {
        if (mockServerClient != null) {
            mockServerClient.close();
            mockServerClient = null;
        }
        if (mockServer != null) {
            mockServer.stop();
            mockServer = null;
        }
        if (clientGroup != null) {
            clientGroup.shutdownGracefully();
            clientGroup = null;
        }
    }

    /**
     * Server-streaming: a single unary request produces three response messages (one with a
     * per-message delay) framed as separate DATA frames, then grpc-status=0 in trailing HEADERS.
     */
    @Test
    public void shouldHandleServerStreamingGrpcOverHttp3() throws Exception {
        int http3Port = startMockServer(() -> configuration()
            .grpcDescriptorDirectory(DESCRIPTOR_DIR.toString())
            .attemptToProxyIfNoMatchingExpectation(false));

        mockServerClient.when(
            request().withMethod("POST").withPath("/" + SERVICE + "/ListGreetings")
        ).respondWithGrpcStream(
            GrpcStreamResponse.grpcStreamResponse()
                .withStatusName("OK")
                .withMessage("{\"greeting\": \"Hello 1\"}")
                .withMessage("{\"greeting\": \"Hello 2\"}", org.mockserver.model.Delay.milliseconds(20))
                .withMessage("{\"greeting\": \"Hello 3\"}")
        );

        byte[] grpcFrame = GrpcFrameCodec.encode(converter.toProtobuf(
            "{\"name\":\"World\"}",
            descriptorStore.getMethod(SERVICE, "ListGreetings").getInputType()
        ));

        GrpcH3Response result = sendGrpcOverHttp3(http3Port, "/" + SERVICE + "/ListGreetings",
            java.util.Collections.singletonList(grpcFrame));

        assertThat("initial :status should be 200", result.initialStatus, is("200"));
        assertThat("initial content-type should be application/grpc",
            result.initialHeaders.get("content-type"), is(GrpcStatusMapper.GRPC_CONTENT_TYPE));
        assertThat("grpc-status must NOT be in initial headers",
            result.initialHeaders.containsKey(GrpcStatusMapper.GRPC_STATUS_HEADER), is(false));
        assertThat("trailing grpc-status should be 0",
            result.trailingHeaders.get(GrpcStatusMapper.GRPC_STATUS_HEADER), is("0"));

        List<byte[]> messages = GrpcFrameCodec.decode(result.bodyBytes);
        assertThat("should receive three streamed messages", messages.size(), is(3));
        assertThat(decode(messages.get(0), "ListGreetings"), containsString("Hello 1"));
        assertThat(decode(messages.get(1), "ListGreetings"), containsString("Hello 2"));
        assertThat(decode(messages.get(2), "ListGreetings"), containsString("Hello 3"));
    }

    /**
     * Bidi-streaming: two inbound request messages are matched against rules and produce two
     * response messages; grpc-status=0 is sent in trailing HEADERS after FIN.
     */
    @Test
    public void shouldHandleBidiStreamingGrpcOverHttp3() throws Exception {
        int http3Port = startMockServer(() -> configuration()
            .grpcDescriptorDirectory(DESCRIPTOR_DIR.toString())
            .grpcBidiStreamingEnabled(true)
            .attemptToProxyIfNoMatchingExpectation(false));

        mockServerClient.when(
            request().withMethod("POST").withPath("/" + SERVICE + "/Chat")
        ).respondWithGrpcBidi(
            GrpcBidiResponse.grpcBidiResponse()
                .withStatusName("OK")
                .withRule(GrpcBidiRule.grpcBidiRule(".*Alice.*").withResponse("{\"greeting\": \"Hello Alice\"}"))
                .withRule(GrpcBidiRule.grpcBidiRule(".*Bob.*").withResponse("{\"greeting\": \"Hello Bob\"}"))
        );

        byte[] alice = GrpcFrameCodec.encode(converter.toProtobuf(
            "{\"name\":\"Alice\"}", descriptorStore.getMethod(SERVICE, "Chat").getInputType()));
        byte[] bob = GrpcFrameCodec.encode(converter.toProtobuf(
            "{\"name\":\"Bob\"}", descriptorStore.getMethod(SERVICE, "Chat").getInputType()));

        GrpcH3Response result = sendGrpcOverHttp3(http3Port, "/" + SERVICE + "/Chat",
            java.util.Arrays.asList(alice, bob));

        assertThat("initial :status should be 200", result.initialStatus, is("200"));
        assertThat("trailing grpc-status should be 0",
            result.trailingHeaders.get(GrpcStatusMapper.GRPC_STATUS_HEADER), is("0"));

        List<byte[]> messages = GrpcFrameCodec.decode(result.bodyBytes);
        assertThat("should receive two rule-driven responses", messages.size(), is(2));
        assertThat(decode(messages.get(0), "Chat"), containsString("Hello Alice"));
        assertThat(decode(messages.get(1), "Chat"), containsString("Hello Bob"));

        mockServerClient.verify(request().withPath("/" + SERVICE + "/Chat"));
    }

    /**
     * When bidi streaming is NOT enabled, a Chat request with a GrpcBidiResponse expectation
     * falls through to the normal pipeline (HttpActionHandler responds 501), confirming the
     * H3 bidi branch is correctly gated by {@code grpcBidiStreamingEnabled}.
     */
    @Test
    public void shouldNotRouteBidiWhenDisabledOverHttp3() throws Exception {
        int http3Port = startMockServer(() -> configuration()
            .grpcDescriptorDirectory(DESCRIPTOR_DIR.toString())
            .attemptToProxyIfNoMatchingExpectation(false));

        mockServerClient.when(
            request().withMethod("POST").withPath("/" + SERVICE + "/Chat")
        ).respondWithGrpcBidi(
            GrpcBidiResponse.grpcBidiResponse()
                .withStatusName("OK")
                .withRule(GrpcBidiRule.grpcBidiRule(".*Alice.*").withResponse("{\"greeting\": \"Hello Alice\"}"))
        );

        byte[] alice = GrpcFrameCodec.encode(converter.toProtobuf(
            "{\"name\":\"Alice\"}", descriptorStore.getMethod(SERVICE, "Chat").getInputType()));

        GrpcH3Response result = sendGrpcOverHttp3(http3Port, "/" + SERVICE + "/Chat",
            java.util.Collections.singletonList(alice));

        // With bidi disabled, the GRPC_BIDI_RESPONSE action falls through to HttpActionHandler
        // (which responds 501); over the gRPC/HTTP3 writer that 501 surfaces as a gRPC error
        // (non-zero grpc-status, no streamed rule responses) -- crucially NOT the bidi rule output.
        String grpcStatus = result.trailingHeaders.get(GrpcStatusMapper.GRPC_STATUS_HEADER);
        if (grpcStatus == null) {
            grpcStatus = result.initialHeaders.get(GrpcStatusMapper.GRPC_STATUS_HEADER);
        }
        assertThat("bidi rule responses must NOT be produced when bidi is disabled",
            result.bodyBytes.length, is(0));
        // 501 has no row in the gRPC HTTP-to-status table, so it maps to UNKNOWN (2)
        assertThat("grpc-status should be UNKNOWN when bidi is disabled",
            grpcStatus, is("2"));
    }

    /**
     * End-to-end INBOUND_STREAM breakpoint over a real HTTP/3 (QUIC) gRPC-bidi client:
     * an interactive callback-WebSocket client DROPs the inbound frame carrying "Alice" and
     * CONTINUEs the rest. The breakpoint must intercept both inbound frames, and dropping
     * Alice's request frame must suppress its response so only "Hello Bob" is returned.
     */
    @Test
    public void shouldDropInboundBidiFrameOverHttp3ViaBreakpoint() throws Exception {
        int http3Port = startMockServer(() -> configuration()
            .grpcDescriptorDirectory(DESCRIPTOR_DIR.toString())
            .grpcBidiStreamingEnabled(true)
            .attemptToProxyIfNoMatchingExpectation(false));

        mockServerClient.when(
            request().withMethod("POST").withPath("/" + SERVICE + "/Chat")
        ).respondWithGrpcBidi(
            GrpcBidiResponse.grpcBidiResponse()
                .withStatusName("OK")
                .withRule(GrpcBidiRule.grpcBidiRule(".*Alice.*").withResponse("{\"greeting\": \"Hello Alice\"}"))
                .withRule(GrpcBidiRule.grpcBidiRule(".*Bob.*").withResponse("{\"greeting\": \"Hello Bob\"}"))
        );

        List<String> intercepted = new CopyOnWriteArrayList<>();
        mockServerClient.addBreakpoint(
            request().withPath("/" + SERVICE + "/Chat"),
            EnumSet.of(BreakpointPhase.INBOUND_STREAM),
            pausedFrame -> {
                String json = decodeInboundFrame(pausedFrame, "Chat");
                intercepted.add(pausedFrame.getDirection() + "|" + pausedFrame.getPhase() + "|" + json);
                String action = json.contains("Alice") ? "DROP" : "CONTINUE";
                return new StreamFrameDecisionDTO()
                    .setCorrelationId(pausedFrame.getCorrelationId())
                    .setAction(action);
            });

        GrpcH3Response result = sendGrpcOverHttp3(http3Port, "/" + SERVICE + "/Chat",
            java.util.Arrays.asList(chatFrame("Alice"), chatFrame("Bob")));

        assertThat("trailing grpc-status should be 0",
            result.trailingHeaders.get(GrpcStatusMapper.GRPC_STATUS_HEADER), is("0"));
        assertThat("breakpoint should have intercepted both inbound frames", intercepted.size(), is(2));
        assertThat("both frames are client->server INBOUND_STREAM",
            intercepted.get(0), containsString("INBOUND|INBOUND_STREAM|"));
        assertThat(intercepted.get(1), containsString("INBOUND|INBOUND_STREAM|"));

        List<byte[]> messages = GrpcFrameCodec.decode(result.bodyBytes);
        assertThat("dropping the Alice inbound frame yields only Bob's response", messages.size(), is(1));
        assertThat(decode(messages.get(0), "Chat"), containsString("Hello Bob"));
        assertThat(decode(messages.get(0), "Chat"), not(containsString("Alice")));
    }

    /**
     * End-to-end CONTINUE: the breakpoint intercepts every inbound bidi frame over real HTTP/3
     * and continues it unchanged, so the stream behaves exactly as without a breakpoint (both
     * rule responses returned) -- proving interception is transparent on the continue path.
     */
    @Test
    public void shouldContinueInboundBidiFramesOverHttp3ViaBreakpoint() throws Exception {
        int http3Port = startMockServer(() -> configuration()
            .grpcDescriptorDirectory(DESCRIPTOR_DIR.toString())
            .grpcBidiStreamingEnabled(true)
            .attemptToProxyIfNoMatchingExpectation(false));

        mockServerClient.when(
            request().withMethod("POST").withPath("/" + SERVICE + "/Chat")
        ).respondWithGrpcBidi(
            GrpcBidiResponse.grpcBidiResponse()
                .withStatusName("OK")
                .withRule(GrpcBidiRule.grpcBidiRule(".*Alice.*").withResponse("{\"greeting\": \"Hello Alice\"}"))
                .withRule(GrpcBidiRule.grpcBidiRule(".*Bob.*").withResponse("{\"greeting\": \"Hello Bob\"}"))
        );

        List<String> intercepted = new CopyOnWriteArrayList<>();
        mockServerClient.addBreakpoint(
            request().withPath("/" + SERVICE + "/Chat"),
            EnumSet.of(BreakpointPhase.INBOUND_STREAM),
            pausedFrame -> {
                intercepted.add(decodeInboundFrame(pausedFrame, "Chat"));
                return new StreamFrameDecisionDTO()
                    .setCorrelationId(pausedFrame.getCorrelationId())
                    .setAction("CONTINUE");
            });

        GrpcH3Response result = sendGrpcOverHttp3(http3Port, "/" + SERVICE + "/Chat",
            java.util.Arrays.asList(chatFrame("Alice"), chatFrame("Bob")));

        assertThat("trailing grpc-status should be 0",
            result.trailingHeaders.get(GrpcStatusMapper.GRPC_STATUS_HEADER), is("0"));
        assertThat("breakpoint intercepted both inbound frames in order", intercepted.size(), is(2));
        assertThat(intercepted.get(0), containsString("Alice"));
        assertThat(intercepted.get(1), containsString("Bob"));

        List<byte[]> messages = GrpcFrameCodec.decode(result.bodyBytes);
        assertThat("continue preserves both rule responses", messages.size(), is(2));
        assertThat(decode(messages.get(0), "Chat"), containsString("Hello Alice"));
        assertThat(decode(messages.get(1), "Chat"), containsString("Hello Bob"));
    }

    /**
     * End-to-end MODIFY: the breakpoint rewrites the inbound "Alice" request frame to a "Bob"
     * request frame over real HTTP/3, so the modified bytes reach the rule matcher and the
     * server returns "Hello Bob" -- proving a modified inbound frame is what gets processed.
     */
    @Test
    public void shouldModifyInboundBidiFrameOverHttp3ViaBreakpoint() throws Exception {
        int http3Port = startMockServer(() -> configuration()
            .grpcDescriptorDirectory(DESCRIPTOR_DIR.toString())
            .grpcBidiStreamingEnabled(true)
            .attemptToProxyIfNoMatchingExpectation(false));

        mockServerClient.when(
            request().withMethod("POST").withPath("/" + SERVICE + "/Chat")
        ).respondWithGrpcBidi(
            GrpcBidiResponse.grpcBidiResponse()
                .withStatusName("OK")
                .withRule(GrpcBidiRule.grpcBidiRule(".*Alice.*").withResponse("{\"greeting\": \"Hello Alice\"}"))
                .withRule(GrpcBidiRule.grpcBidiRule(".*Bob.*").withResponse("{\"greeting\": \"Hello Bob\"}"))
        );

        mockServerClient.addBreakpoint(
            request().withPath("/" + SERVICE + "/Chat"),
            EnumSet.of(BreakpointPhase.INBOUND_STREAM),
            pausedFrame -> {
                StreamFrameDecisionDTO decision = new StreamFrameDecisionDTO()
                    .setCorrelationId(pausedFrame.getCorrelationId());
                String json = decodeInboundFrame(pausedFrame, "Chat");
                if (json.contains("Alice")) {
                    return decision.setAction("MODIFY").setBody(Base64.getEncoder().encodeToString(chatFrame("Bob")));
                }
                return decision.setAction("CONTINUE");
            });

        GrpcH3Response result = sendGrpcOverHttp3(http3Port, "/" + SERVICE + "/Chat",
            java.util.Collections.singletonList(chatFrame("Alice")));

        assertThat("trailing grpc-status should be 0",
            result.trailingHeaders.get(GrpcStatusMapper.GRPC_STATUS_HEADER), is("0"));

        List<byte[]> messages = GrpcFrameCodec.decode(result.bodyBytes);
        assertThat("the modified Bob frame should drive the response", messages.size(), is(1));
        assertThat(decode(messages.get(0), "Chat"), containsString("Hello Bob"));
        assertThat(decode(messages.get(0), "Chat"), not(containsString("Alice")));
    }

    // ---- helpers ----

    private String decode(byte[] protobuf, String method) {
        return converter.toJson(protobuf, descriptorStore.getMethod(SERVICE, method).getOutputType());
    }

    /** Encode a {@code {"name":<who>}} Chat request message as a single gRPC-framed DATA payload. */
    private byte[] chatFrame(String who) {
        return GrpcFrameCodec.encode(converter.toProtobuf(
            "{\"name\":\"" + who + "\"}", descriptorStore.getMethod(SERVICE, "Chat").getInputType()));
    }

    /** Decode a paused INBOUND stream frame DTO back to its request-message JSON. */
    private String decodeInboundFrame(PausedStreamFrameDTO pausedFrame, String method) {
        byte[] frameBytes = Base64.getDecoder().decode(pausedFrame.getBody());
        List<byte[]> messages = GrpcFrameCodec.decode(frameBytes);
        return converter.toJson(messages.get(0), descriptorStore.getMethod(SERVICE, method).getInputType());
    }

    /**
     * Start a MockServer with HTTP/3 (QUIC) on a UDP port it holds; see {@link Http3TestServer}.
     * <p>
     * The {@code configFactory} must build a fresh {@link Configuration} on each call; this helper
     * sets {@code http3Port}. Returns the bound HTTP/3 port.
     */
    private int startMockServer(java.util.function.Supplier<Configuration> configFactory) {
        // give the server QUIC connection generous idle headroom (matching the client's 30s) so a
        // slow handshake or a stream legitimately paused at a breakpoint under load is not torn
        // down by the default 5s server idle timeout
        mockServer = startWithHttp3(udpPort -> new MockServer(configFactory.get().http3Port(udpPort).http3MaxIdleTimeout(30000L), 0));
        mockServerClient = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        return mockServer.getHttp3Port();
    }

    static class GrpcH3Response {
        final String initialStatus;
        final Map<String, String> initialHeaders;
        final Map<String, String> trailingHeaders;
        final byte[] bodyBytes;

        GrpcH3Response(String initialStatus, Map<String, String> initialHeaders,
                       Map<String, String> trailingHeaders, byte[] bodyBytes) {
            this.initialStatus = initialStatus;
            this.initialHeaders = initialHeaders;
            this.trailingHeaders = trailingHeaders;
            this.bodyBytes = bodyBytes;
        }
    }

    /**
     * Send a gRPC request over HTTP/3 as one HEADERS frame followed by the given DATA frames
     * (the last carries FIN), and capture all response DATA bytes plus the initial and trailing
     * HEADERS frames. Works for unary, server-streaming, and (client sends all frames up-front)
     * bidi-streaming.
     */
    private GrpcH3Response sendGrpcOverHttp3(int port, String path, List<byte[]> requestFrames) throws Exception {
        QuicConnection connection = connectQuicWithRetry(port);
        Channel clientChannel = connection.datagramChannel;
        QuicChannel quicChannel = connection.quicChannel;

        List<Http3HeadersFrame> headerFrames = new ArrayList<>();
        BlockingQueue<Boolean> doneQueue = new LinkedBlockingQueue<>();
        ByteArrayOutputStream bodyAccumulator = new ByteArrayOutputStream();

        QuicStreamChannel requestStream = Http3.newRequestStream(
            quicChannel,
            new Http3RequestStreamInboundHandler() {
                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame headersFrame) {
                    synchronized (headerFrames) {
                        headerFrames.add(headersFrame);
                    }
                }

                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame dataFrame) {
                    ByteBuf content = dataFrame.content();
                    byte[] bytes = new byte[content.readableBytes()];
                    content.readBytes(bytes);
                    content.release();
                    synchronized (bodyAccumulator) {
                        bodyAccumulator.write(bytes, 0, bytes.length);
                    }
                }

                @Override
                protected void channelInputClosed(ChannelHandlerContext ctx) {
                    doneQueue.offer(true);
                    ctx.close();
                }
            }
        ).sync().getNow();

        DefaultHttp3HeadersFrame requestHeaders = new DefaultHttp3HeadersFrame();
        requestHeaders.headers().method("POST");
        requestHeaders.headers().path(path);
        requestHeaders.headers().scheme("https");
        requestHeaders.headers().authority("127.0.0.1:" + port);
        requestHeaders.headers().add("content-type", GrpcStatusMapper.GRPC_CONTENT_TYPE);
        requestHeaders.headers().add("te", "trailers");
        requestStream.writeAndFlush(requestHeaders).sync();

        for (int i = 0; i < requestFrames.size(); i++) {
            DefaultHttp3DataFrame data = new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(requestFrames.get(i)));
            if (i == requestFrames.size() - 1) {
                requestStream.writeAndFlush(data).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync();
            } else {
                requestStream.writeAndFlush(data).sync();
            }
        }

        // honour the completion signal: the trailing HEADERS frame (grpc-status) only arrives
        // once channelInputClosed fires, so wait for it before reading the accumulated frames
        Boolean done = doneQueue.poll(10, TimeUnit.SECONDS);
        org.junit.Assert.assertNotNull(
            "timed out waiting for gRPC response/trailers over HTTP/3", done);

        byte[] responseBody;
        synchronized (bodyAccumulator) {
            responseBody = bodyAccumulator.toByteArray();
        }

        quicChannel.close().sync();
        clientChannel.close().sync();

        Map<String, String> initialHeaders = new ConcurrentHashMap<>();
        Map<String, String> trailingHeaders = new ConcurrentHashMap<>();
        String initialStatus = "null";
        synchronized (headerFrames) {
            if (!headerFrames.isEmpty()) {
                Http3HeadersFrame first = headerFrames.get(0);
                CharSequence status = first.headers().status();
                initialStatus = status != null ? status.toString() : "null";
                first.headers().forEach(entry -> {
                    String name = entry.getKey().toString();
                    if (!name.startsWith(":")) {
                        initialHeaders.put(name, entry.getValue().toString());
                    }
                });
            }
            if (headerFrames.size() > 1) {
                Http3HeadersFrame trailing = headerFrames.get(headerFrames.size() - 1);
                trailing.headers().forEach(entry -> {
                    String name = entry.getKey().toString();
                    if (!name.startsWith(":")) {
                        trailingHeaders.put(name, entry.getValue().toString());
                    }
                });
            }
        }

        return new GrpcH3Response(initialStatus, initialHeaders, trailingHeaders, responseBody);
    }

    /** Holds the datagram channel + QUIC channel created by {@link #connectQuicWithRetry(int)}. */
    private static class QuicConnection {
        final Channel datagramChannel;
        final QuicChannel quicChannel;

        QuicConnection(Channel datagramChannel, QuicChannel quicChannel) {
            this.datagramChannel = datagramChannel;
            this.quicChannel = quicChannel;
        }
    }

    /**
     * Establish a QUIC/HTTP3 connection to {@code 127.0.0.1:port} with a bounded retry.
     * <p>
     * A single lost UDP datagram on loopback (or a cold-start/GC hiccup) can stall the QUIC
     * handshake. To stop that from flaking the test, each attempt fully tears down its
     * {@link NioEventLoopGroup}, datagram channel and (partially-built) QUIC channel before the
     * next attempt, and only the final failure is rethrown. The client codec's
     * {@code maxIdleTimeout} is given generous headroom (30s) over the per-attempt connect
     * deadline (8s) so an in-progress handshake is never torn down by the idle timer.
     */
    private QuicConnection connectQuicWithRetry(int port) throws Exception {
        final int maxAttempts = 3;
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (clientGroup != null) {
                clientGroup.shutdownGracefully().sync();
            }
            clientGroup = new NioEventLoopGroup(1);

            QuicSslContext clientSslContext = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(Http3.supportedApplicationProtocols())
                .build();

            Channel clientChannel = null;
            try {
                clientChannel = new Bootstrap()
                    .group(clientGroup)
                    .channelFactory(Ipv4DatagramChannelFactory.INSTANCE)
                    .handler(Http3.newQuicClientCodecBuilder()
                        .sslContext(clientSslContext)
                        // generous idle headroom over the 8s per-attempt connect deadline so an
                        // in-progress handshake is not torn down by the idle timer
                        .maxIdleTimeout(30000, TimeUnit.MILLISECONDS)
                        .initialMaxData(10000000)
                        .initialMaxStreamDataBidirectionalLocal(1000000)
                        .initialMaxStreamsBidirectional(100)
                        .build())
                    .bind(0)
                    .sync()
                    .channel();

                QuicChannel quicChannel = QuicChannel.newBootstrap(clientChannel)
                    .handler(new Http3ClientConnectionHandler())
                    .remoteAddress(new InetSocketAddress("127.0.0.1", port))
                    .connect()
                    .get(8, TimeUnit.SECONDS);

                return new QuicConnection(clientChannel, quicChannel);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
                // transient QUIC connect stall: tear down everything created this attempt and retry
                lastFailure = e;
                if (clientChannel != null) {
                    try {
                        clientChannel.close().sync();
                    } catch (Exception ignore) {
                        // best-effort teardown
                    }
                }
                if (clientGroup != null) {
                    clientGroup.shutdownGracefully().sync();
                    clientGroup = null;
                }
                if (attempt == maxAttempts) {
                    throw e;
                }
            }
        }
        // unreachable: the loop either returns or rethrows on the final attempt
        throw lastFailure;
    }

    private static void assumeQuicAvailable() {
        try {
            Assume.assumeTrue(
                "native QUIC transport not available on this platform -- skipping HTTP/3 gRPC streaming test",
                io.netty.handler.codec.quic.Quic.isAvailable()
            );
        } catch (Throwable t) {
            Assume.assumeNoException("native QUIC transport failed to load -- skipping HTTP/3 gRPC streaming test", t);
        }
    }
}
