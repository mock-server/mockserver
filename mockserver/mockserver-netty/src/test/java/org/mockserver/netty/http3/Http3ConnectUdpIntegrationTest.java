package org.mockserver.netty.http3;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
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
import org.mockserver.netty.MockServer;
import org.mockserver.testing.socket.Ipv4DatagramChannelFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.http3.Http3TestServer.startWithHttp3;

/**
 * Integration tests for the HTTP/3 CONNECT-UDP (MASQUE) relay with a real QUIC
 * server, QUIC client, and UDP echo server.
 * <p>
 * Tests skip gracefully when the native QUIC transport (BoringSSL) is not
 * available on the build platform.
 */
@SuppressWarnings("deprecation") // NioEventLoopGroup deprecation in Netty 4.2
public class Http3ConnectUdpIntegrationTest {

    private MockServer mockServer;
    private NioEventLoopGroup clientGroup;
    private Channel udpEchoChannel;
    private NioEventLoopGroup echoGroup;

    @Before
    public void setUp() {
        assumeQuicAvailable();
    }

    @After
    public void tearDown() {
        if (mockServer != null) {
            mockServer.stop();
            mockServer = null;
        }
        if (clientGroup != null) {
            clientGroup.shutdownGracefully();
            clientGroup = null;
        }
        if (udpEchoChannel != null) {
            udpEchoChannel.close();
            udpEchoChannel = null;
        }
        if (echoGroup != null) {
            echoGroup.shutdownGracefully();
            echoGroup = null;
        }
    }

    /**
     * Core test: CONNECT-UDP relay round-trips a datagram through a local UDP
     * echo server. Verifies the full flow: client sends extended CONNECT with
     * :protocol=connect-udp, gets 200 OK, sends data, receives the echoed data
     * back through the relay.
     */
    @Test
    public void shouldRelayDatagramThroughConnectUdpTunnel() throws Exception {
        // Start a local UDP echo server
        int echoPort = startUdpEchoServer();

        // Start MockServer with HTTP/3 + CONNECT-UDP enabled
        Configuration config = configuration()
            .http3MaxIdleTimeout(30000L)
            .http3ConnectUdpEnabled(true);

        mockServer = startWithHttp3(config);

        int http3Port = mockServer.getHttp3Port();

        // Send extended CONNECT with :protocol=connect-udp to the relay
        String testPayload = "hello-masque-relay";
        String[] result = sendConnectUdpAndRelay(http3Port, "127.0.0.1:" + echoPort, testPayload);

        assertThat("status should be 200 (tunnel established)", result[0], is("200"));
        assertThat("echoed datagram should match sent payload", result[1], is(testPayload));
    }

    @Test
    public void shouldRelayWhenTargetIsInAllowlist() throws Exception {
        int echoPort = startUdpEchoServer();

        Configuration config = configuration()
            .http3MaxIdleTimeout(30000L)
            .http3ConnectUdpEnabled(true)
            // host-only allowlist entry permits the loopback echo target on any port
            .http3ConnectUdpAllowedTargets("127.0.0.1");

        mockServer = startWithHttp3(config);

        int http3Port = mockServer.getHttp3Port();

        String testPayload = "hello-allowlisted-relay";
        String[] result = sendConnectUdpAndRelay(http3Port, "127.0.0.1:" + echoPort, testPayload);

        assertThat("status should be 200 for an allowlisted target", result[0], is("200"));
        assertThat("echoed datagram should match sent payload", result[1], is(testPayload));
    }

    @Test
    public void shouldRejectConnectUdpWhenTargetNotInAllowlist() throws Exception {
        int echoPort = startUdpEchoServer();

        Configuration config = configuration()
            .http3MaxIdleTimeout(30000L)
            .http3ConnectUdpEnabled(true)
            // only a different target is permitted, so the echo target must be refused
            .http3ConnectUdpAllowedTargets("allowed.example.com:443");

        mockServer = startWithHttp3(config);

        int http3Port = mockServer.getHttp3Port();

        String[] result = sendConnectUdpRequest(http3Port, "127.0.0.1:" + echoPort);
        assertThat("status should be 403 for a non-allowlisted target", result[0], is("403"));
        assertThat("body should be the generic refusal (no internal detail leaked)",
            result[1], containsString("CONNECT-UDP target not permitted"));
        assertThat("body must NOT leak the allowlist property name",
            result[1], not(containsString("http3ConnectUdpAllowedTargets")));
    }

    @Test
    public void shouldRejectConnectUdpToPrivateNetworkWhenSsrfBlockingEnabled() throws Exception {
        int echoPort = startUdpEchoServer();

        Configuration config = configuration()
            .http3MaxIdleTimeout(30000L)
            .http3ConnectUdpEnabled(true)
            // reuse the forward-proxy SSRF block; loopback is a blocked private address
            .forwardProxyBlockPrivateNetworks(true);

        mockServer = startWithHttp3(config);

        int http3Port = mockServer.getHttp3Port();

        String[] result = sendConnectUdpRequest(http3Port, "127.0.0.1:" + echoPort);
        assertThat("status should be 403 for a blocked loopback target", result[0], is("403"));
        assertThat("body should be the generic refusal (no internal host/reason leaked)",
            result[1], containsString("CONNECT-UDP target not permitted"));
        assertThat("body must NOT leak the blocked host",
            result[1], not(containsString("127.0.0.1")));
    }

    @Test
    public void shouldRejectConnectUdpWithInvalidAuthority() throws Exception {
        Configuration config = configuration()
            .http3MaxIdleTimeout(30000L)
            .http3ConnectUdpEnabled(true);

        mockServer = startWithHttp3(config);

        int http3Port = mockServer.getHttp3Port();

        // Send extended CONNECT with invalid authority (no port)
        String[] result = sendConnectUdpRequest(http3Port, "no-port-authority");
        assertThat("status should be 400 for invalid authority", result[0], is("400"));
        assertThat("body should explain invalid authority",
            result[1], containsString("Invalid :authority"));
    }

    @Test
    public void shouldStillServeNormalRequestsWhenFlagEnabled() throws Exception {
        Configuration config = configuration()
            .http3MaxIdleTimeout(30000L)
            .http3ConnectUdpEnabled(true);

        mockServer = startWithHttp3(config);

        int http3Port = mockServer.getHttp3Port();

        // Set up an expectation via HTTP/1.1 client API
        MockServerClient client = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
        client.when(
            request().withMethod("GET").withPath("/normal")
        ).respond(
            response()
                .withStatusCode(200)
                .withBody("normal-response")
        );

        // Send a normal GET request over HTTP/3
        String[] result = sendHttp3Request(http3Port, "GET", "/normal", null);
        assertThat("status should be 200 for normal GET", result[0], is("200"));
        assertThat("body should be the expectation response",
            result[1], containsString("normal-response"));
    }

    @Test
    public void shouldNotInterceptConnectWhenFlagDisabled() throws Exception {
        Configuration config = configuration()
            .http3MaxIdleTimeout(30000L)
            .http3ConnectUdpEnabled(false) // explicitly disabled
            .attemptToProxyIfNoMatchingExpectation(false); // else the unmatched CONNECT is proxied upstream

        mockServer = startWithHttp3(config);

        int http3Port = mockServer.getHttp3Port();

        // Send a plain CONNECT request -- without the handler in the pipeline,
        // the normal mock handler will process it (likely returning 404 or
        // trying to match an expectation)
        String[] result = sendHttp3ConnectRequest(http3Port, "target.example.com:443");

        // Without the CONNECT-UDP handler, the request goes to the normal
        // pipeline, which matches no expectation for it and does not proxy it
        assertThat("CONNECT should reach the normal pipeline when the flag is disabled",
            result[0], is("404"));
    }

    @Test
    public void shouldDefaultToDisabled() {
        Configuration config = configuration();
        assertThat("http3ConnectUdpEnabled should default to false",
            config.http3ConnectUdpEnabled(), is(false));
    }

    // ---- UDP echo server ----

    /**
     * Start a simple UDP echo server on an ephemeral port.
     *
     * @return the port the echo server is bound to
     */
    private int startUdpEchoServer() throws Exception {
        echoGroup = new NioEventLoopGroup(1);
        udpEchoChannel = new Bootstrap()
            .group(echoGroup)
            .channelFactory(Ipv4DatagramChannelFactory.INSTANCE)
            .handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    if (msg instanceof DatagramPacket) {
                        DatagramPacket packet = (DatagramPacket) msg;
                        // Echo the datagram back to the sender
                        DatagramPacket echo = new DatagramPacket(
                            packet.content().retain(),
                            packet.sender()
                        );
                        ctx.writeAndFlush(echo);
                        packet.release();
                    }
                }
            })
            .bind(0)
            .sync()
            .channel();

        return ((InetSocketAddress) udpEchoChannel.localAddress()).getPort();
    }

    // ---- HTTP/3 client helpers ----

    /**
     * Send an extended CONNECT-UDP request, then relay a test payload through
     * the tunnel and return [status, echoed-data].
     */
    private String[] sendConnectUdpAndRelay(int port, String authority, String payload) throws Exception {
        if (clientGroup != null) {
            clientGroup.shutdownGracefully().sync();
        }
        clientGroup = new NioEventLoopGroup(1);

        QuicSslContext clientSslContext = QuicSslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols(Http3.supportedApplicationProtocols())
            .build();

        Channel clientChannel = new Bootstrap()
            .group(clientGroup)
            .channelFactory(Ipv4DatagramChannelFactory.INSTANCE)
            .handler(Http3.newQuicClientCodecBuilder()
                .sslContext(clientSslContext)
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
            .get(15, TimeUnit.SECONDS);

        BlockingQueue<String> statusQueue = new LinkedBlockingQueue<>();
        BlockingQueue<String> dataQueue = new LinkedBlockingQueue<>();

        QuicStreamChannel requestStream = Http3.newRequestStream(
            quicChannel,
            new Http3RequestStreamInboundHandler() {
                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame headersFrame) {
                    CharSequence status = headersFrame.headers().status();
                    statusQueue.offer(status != null ? status.toString() : "null");
                }

                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame dataFrame) {
                    ByteBuf content = dataFrame.content();
                    dataQueue.offer(content.toString(StandardCharsets.UTF_8));
                    content.release();
                }

                @Override
                protected void channelInputClosed(ChannelHandlerContext ctx) {
                    ctx.close();
                }
            }
        ).sync().getNow();

        // Send extended CONNECT with :protocol=connect-udp
        DefaultHttp3HeadersFrame requestHeaders = new DefaultHttp3HeadersFrame();
        requestHeaders.headers().method("CONNECT");
        requestHeaders.headers().protocol("connect-udp");
        requestHeaders.headers().authority(authority);
        requestHeaders.headers().scheme("https");
        requestHeaders.headers().path("/");

        requestStream.writeAndFlush(requestHeaders).sync();

        // Wait for the 200 response (tunnel established)
        String status = statusQueue.poll(20, TimeUnit.SECONDS);
        if (!"200".equals(status)) {
            // Tunnel not established -- read any error body
            String errorBody = dataQueue.poll(2, TimeUnit.SECONDS);
            quicChannel.close().sync();
            clientChannel.close().sync();
            return new String[]{
                status != null ? status : "null",
                errorBody != null ? errorBody : ""
            };
        }

        // Send a datagram through the tunnel as a DATA frame
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        requestStream.writeAndFlush(
            new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(payloadBytes))
        ).sync();

        // Wait for the echoed datagram to come back as a DATA frame
        String echoed = dataQueue.poll(20, TimeUnit.SECONDS);

        quicChannel.close().sync();
        clientChannel.close().sync();

        return new String[]{
            status,
            echoed != null ? echoed : ""
        };
    }

    /**
     * Send an extended CONNECT-UDP request (without relay) and return [status, body].
     */
    private String[] sendConnectUdpRequest(int port, String authority) throws Exception {
        if (clientGroup != null) {
            clientGroup.shutdownGracefully().sync();
        }
        clientGroup = new NioEventLoopGroup(1);

        QuicSslContext clientSslContext = QuicSslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols(Http3.supportedApplicationProtocols())
            .build();

        Channel clientChannel = new Bootstrap()
            .group(clientGroup)
            .channelFactory(Ipv4DatagramChannelFactory.INSTANCE)
            .handler(Http3.newQuicClientCodecBuilder()
                .sslContext(clientSslContext)
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
            .get(15, TimeUnit.SECONDS);

        BlockingQueue<String> statusQueue = new LinkedBlockingQueue<>();
        BlockingQueue<String> bodyQueue = new LinkedBlockingQueue<>();

        QuicStreamChannel requestStream = Http3.newRequestStream(
            quicChannel,
            new Http3RequestStreamInboundHandler() {
                private final StringBuilder bodyBuilder = new StringBuilder();

                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame headersFrame) {
                    CharSequence status = headersFrame.headers().status();
                    statusQueue.offer(status != null ? status.toString() : "null");
                }

                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame dataFrame) {
                    ByteBuf content = dataFrame.content();
                    bodyBuilder.append(content.toString(StandardCharsets.UTF_8));
                    content.release();
                }

                @Override
                protected void channelInputClosed(ChannelHandlerContext ctx) {
                    bodyQueue.offer(bodyBuilder.toString());
                    ctx.close();
                }
            }
        ).sync().getNow();

        // Extended CONNECT with :protocol=connect-udp
        DefaultHttp3HeadersFrame requestHeaders = new DefaultHttp3HeadersFrame();
        requestHeaders.headers().method("CONNECT");
        requestHeaders.headers().protocol("connect-udp");
        requestHeaders.headers().authority(authority);
        requestHeaders.headers().scheme("https");
        requestHeaders.headers().path("/");

        requestStream.writeAndFlush(requestHeaders)
            .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
            .sync();

        String status = statusQueue.poll(20, TimeUnit.SECONDS);
        String responseBody = bodyQueue.poll(20, TimeUnit.SECONDS);

        quicChannel.close().sync();
        clientChannel.close().sync();

        return new String[]{
            status != null ? status : "null",
            responseBody != null ? responseBody : ""
        };
    }

    /**
     * Send a plain HTTP/3 CONNECT request (without :protocol) and return [status, body].
     */
    private String[] sendHttp3ConnectRequest(int port, String authority) throws Exception {
        if (clientGroup != null) {
            clientGroup.shutdownGracefully().sync();
        }
        clientGroup = new NioEventLoopGroup(1);

        QuicSslContext clientSslContext = QuicSslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols(Http3.supportedApplicationProtocols())
            .build();

        Channel clientChannel = new Bootstrap()
            .group(clientGroup)
            .channelFactory(Ipv4DatagramChannelFactory.INSTANCE)
            .handler(Http3.newQuicClientCodecBuilder()
                .sslContext(clientSslContext)
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
            .get(15, TimeUnit.SECONDS);

        BlockingQueue<String> statusQueue = new LinkedBlockingQueue<>();
        BlockingQueue<String> bodyQueue = new LinkedBlockingQueue<>();

        QuicStreamChannel requestStream = Http3.newRequestStream(
            quicChannel,
            new Http3RequestStreamInboundHandler() {
                private final StringBuilder bodyBuilder = new StringBuilder();

                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame headersFrame) {
                    CharSequence status = headersFrame.headers().status();
                    statusQueue.offer(status != null ? status.toString() : "null");
                }

                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame dataFrame) {
                    ByteBuf content = dataFrame.content();
                    bodyBuilder.append(content.toString(StandardCharsets.UTF_8));
                    content.release();
                }

                @Override
                protected void channelInputClosed(ChannelHandlerContext ctx) {
                    bodyQueue.offer(bodyBuilder.toString());
                    ctx.close();
                }
            }
        ).sync().getNow();

        // Plain CONNECT per HTTP/3 = :method + :authority only (no :path, no :scheme)
        DefaultHttp3HeadersFrame requestHeaders = new DefaultHttp3HeadersFrame();
        requestHeaders.headers().method("CONNECT");
        requestHeaders.headers().authority(authority);

        requestStream.writeAndFlush(requestHeaders)
            .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
            .sync();

        String status = statusQueue.poll(20, TimeUnit.SECONDS);
        String responseBody = bodyQueue.poll(20, TimeUnit.SECONDS);

        quicChannel.close().sync();
        clientChannel.close().sync();

        return new String[]{
            status != null ? status : "null",
            responseBody != null ? responseBody : ""
        };
    }

    /**
     * Send a normal HTTP/3 request (GET/POST etc.) and return [status, body].
     */
    private String[] sendHttp3Request(int port, String method, String path, byte[] body) throws Exception {
        if (clientGroup != null) {
            clientGroup.shutdownGracefully().sync();
        }
        clientGroup = new NioEventLoopGroup(1);

        QuicSslContext clientSslContext = QuicSslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols(Http3.supportedApplicationProtocols())
            .build();

        Channel clientChannel = new Bootstrap()
            .group(clientGroup)
            .channelFactory(Ipv4DatagramChannelFactory.INSTANCE)
            .handler(Http3.newQuicClientCodecBuilder()
                .sslContext(clientSslContext)
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
            .get(15, TimeUnit.SECONDS);

        BlockingQueue<String> statusQueue = new LinkedBlockingQueue<>();
        BlockingQueue<String> bodyQueue = new LinkedBlockingQueue<>();

        QuicStreamChannel requestStream = Http3.newRequestStream(
            quicChannel,
            new Http3RequestStreamInboundHandler() {
                private final StringBuilder bodyBuilder = new StringBuilder();

                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame headersFrame) {
                    CharSequence status = headersFrame.headers().status();
                    statusQueue.offer(status != null ? status.toString() : "null");
                }

                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame dataFrame) {
                    ByteBuf content = dataFrame.content();
                    bodyBuilder.append(content.toString(StandardCharsets.UTF_8));
                    content.release();
                }

                @Override
                protected void channelInputClosed(ChannelHandlerContext ctx) {
                    bodyQueue.offer(bodyBuilder.toString());
                    ctx.close();
                }
            }
        ).sync().getNow();

        DefaultHttp3HeadersFrame requestHeaders = new DefaultHttp3HeadersFrame();
        requestHeaders.headers().method(method);
        requestHeaders.headers().path(path);
        requestHeaders.headers().scheme("https");
        requestHeaders.headers().authority("127.0.0.1:" + port);

        if (body != null && body.length > 0) {
            requestHeaders.headers().add("content-type", "application/json");
            requestStream.writeAndFlush(requestHeaders).sync();
            requestStream.writeAndFlush(new DefaultHttp3DataFrame(
                Unpooled.wrappedBuffer(body)
            )).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync();
        } else {
            requestStream.writeAndFlush(requestHeaders)
                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
                .sync();
        }

        String status = statusQueue.poll(20, TimeUnit.SECONDS);
        String responseBody = bodyQueue.poll(20, TimeUnit.SECONDS);

        quicChannel.close().sync();
        clientChannel.close().sync();

        return new String[]{
            status != null ? status : "null",
            responseBody != null ? responseBody : ""
        };
    }

    private static void assumeQuicAvailable() {
        try {
            boolean available = io.netty.handler.codec.quic.Quic.isAvailable();
            Assume.assumeTrue(
                "native QUIC transport not available on this platform -- skipping HTTP/3 test",
                available
            );
        } catch (Throwable t) {
            Assume.assumeNoException(
                "native QUIC transport failed to load -- skipping HTTP/3 test",
                t
            );
        }
    }
}
