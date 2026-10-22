package org.mockserver.netty.unification;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2OutboundFrameLogger;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.logging.RawNetworkTrafficLog;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.action.http.HttpActionHandler;
import org.mockserver.netty.MockServerUnificationInitializer;
import org.mockserver.netty.integration.proxy.direct.StartTlsUpstream;
import org.mockserver.netty.proxy.relay.RelayUpstreamStandIn;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.tls.NettySslContextFactory;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;

/**
 * logRawNetworkTraffic on MockServer's own connection pipeline: off, no handler or frame logger is added to any
 * connection; on, each connection's traffic is logged after TLS decryption with sensitive header values masked.
 */
public class RawNetworkTrafficPipelineTest {

    private static final String SECRET = "Bearer s3cr3t-token-that-must-not-be-logged";
    private static final byte[] HTTP_REQUEST = ascii("PUT /mockserver/status HTTP/1.1\r\nHost: localhost\r\nAuthorization: " + SECRET + "\r\ncontent-length: 0\r\n\r\n");
    private static final byte[] H2C_PREFACE_AND_SETTINGS = concat(ascii("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n"), new byte[]{0, 0, 0, 4, 0, 0, 0, 0, 0});
    private static final byte[] WEBSOCKET_UPGRADE = ascii("GET /_mockserver_callback_websocket HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n");
    private static final byte[] BINARY = {0, 0, 0, 8, 4, (byte) 0xd2, 0x16, 0x2f};

    private static HttpState httpState;

    private final List<String> logged = new CopyOnWriteArrayList<>();
    private final List<EmbeddedChannel> channels = new ArrayList<>();
    private final List<MockServerUnificationInitializer> initializers = new ArrayList<>();
    private final NettyHttpClient httpClient = mock(NettyHttpClient.class);
    private final RelayUpstreamStandIn upstreams = new RelayUpstreamStandIn(httpClient);

    @BeforeClass
    public static void startServerState() {
        httpState = new HttpState(configuration(), new MockServerLogger(), mock(Scheduler.class));
    }

    @AfterClass
    public static void stopServerState() {
        httpState.stop();
        httpState = null;
    }

    @After
    public void releaseChannels() {
        channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        upstreams.releaseAll();
        initializers.forEach(initializer -> initializer.getMcpSessionManager().shutdown());
    }

    @Test
    public void shouldAddNoHandlerToAnyConnectionWhenOff() {
        Configuration configuration = baseConfiguration().logRawNetworkTraffic(false);

        EmbeddedChannel http = connection(initializer(configuration));
        http.writeInbound(Unpooled.wrappedBuffer(HTTP_REQUEST));
        EmbeddedChannel h2c = connection(initializer(configuration));
        h2c.writeInbound(Unpooled.wrappedBuffer(H2C_PREFACE_AND_SETTINGS));
        EmbeddedChannel binary = connection(initializer(configuration));
        binary.writeInbound(Unpooled.wrappedBuffer(BINARY));

        // each connection reached its protocol's pipeline, and none of them has the logger
        assertThat(http.pipeline().get(HttpServerCodec.class), is(notNullValue()));
        assertThat(h2c.pipeline().get(io.netty.handler.codec.http2.Http2FrameCodec.class), is(notNullValue()));
        assertThat(binary.pipeline().get(org.mockserver.netty.proxy.BinaryRequestProxyingHandler.class), is(notNullValue()));
        for (EmbeddedChannel channel : List.of(http, h2c, binary)) {
            assertThat(channel.pipeline().names(), not(hasItem(RawNetworkTrafficLog.HANDLER_NAME)));
            assertThat(rawTrafficHandlers(channel), is(empty()));
        }
    }

    @Test
    public void shouldAddTheLoggerBeforeTheCodecWhenOnInTheConfiguration() {
        EmbeddedChannel http = connection(initializer(baseConfiguration().logRawNetworkTraffic(true)));

        http.writeInbound(Unpooled.wrappedBuffer(HTTP_REQUEST));

        List<String> names = http.pipeline().names();
        assertThat(names, hasItem(RawNetworkTrafficLog.HANDLER_NAME));
        String codec = http.pipeline().context(HttpServerCodec.class).name();
        assertThat(names.indexOf(RawNetworkTrafficLog.HANDLER_NAME), lessThan(names.indexOf(codec)));
    }

    @Test
    public void shouldLogAnHttpRequestAndResponseWithAuthorizationMasked() {
        EmbeddedChannel http = connection(capturingInitializer());

        http.writeInbound(Unpooled.wrappedBuffer(HTTP_REQUEST));
        releaseOutbound(http);

        String all = String.join("\n", logged);
        assertThat(all, containsString("received"));
        assertThat(all, containsString("PUT /mockserver/status HTTP/1.1\\r\\n" + RawNetworkTrafficLog.CONTINUATION));
        assertThat(all, containsString("Authorization: " + RawNetworkTrafficLog.REDACTED));
        assertThat(all, containsString("sent"));
        assertThat(all, containsString("HTTP/1.1 200 OK\\r\\n" + RawNetworkTrafficLog.CONTINUATION));
        assertThat(all, not(containsString(SECRET)));
    }

    @Test
    public void shouldLogHttp2AsFramesNotBytes() {
        EmbeddedChannel h2c = connection(capturingInitializer());

        h2c.writeInbound(Unpooled.wrappedBuffer(H2C_PREFACE_AND_SETTINGS));
        releaseOutbound(h2c);

        assertThat(h2c.pipeline().names(), not(hasItem(RawNetworkTrafficLog.HANDLER_NAME)));
        String all = String.join("\n", logged);
        assertThat(all, containsString("received SETTINGS frame: ack=false"));
        assertThat(all, containsString("sent SETTINGS frame"));
    }

    @Test
    public void shouldHexDumpABinaryConnection() {
        EmbeddedChannel binary = connection(capturingInitializer());

        binary.writeInbound(Unpooled.wrappedBuffer(BINARY));

        assertThat(String.join("\n", logged), containsString("00 00 00 08 04 d2 16 2f"));
    }

    @Test
    public void shouldLogTheDecryptedBytesOfATlsConnection() throws Exception {
        Map<String, ChannelHandler> handlers = exchangeOverSocket(tlsInitializer(new RawNetworkTrafficLog(RawNetworkTrafficLog.SERVER_SIDE, () -> null, logged::add)), true, HTTP_REQUEST, "HTTP/1.1 200 OK");

        awaitLogged("HTTP/1.1 200 OK");
        assertThat(handlers.keySet(), hasItem(RawNetworkTrafficLog.HANDLER_NAME));
        String all = String.join("\n", logged);
        assertThat(all, containsString("PUT /mockserver/status HTTP/1.1\\r\\n" + RawNetworkTrafficLog.CONTINUATION));
        assertThat(all, containsString("Authorization: " + RawNetworkTrafficLog.REDACTED));
        assertThat(all, not(containsString(SECRET)));
        // nothing before the TLS handler is logged: no handshake record
        assertThat(all, not(containsString("\\x16\\x03")));
    }

    @Test
    public void shouldAddNoHandlerToATlsConnectionWhenOff() throws Exception {
        Map<String, ChannelHandler> handlers = exchangeOverSocket(tlsInitializer(null), true, HTTP_REQUEST, "HTTP/1.1 200 OK");

        assertThat(hasHandler(handlers, io.netty.handler.ssl.SslHandler.class), is(true));
        assertThat(hasHandler(handlers, HttpServerCodec.class), is(true));
        assertThat(handlers.keySet(), not(hasItem(RawNetworkTrafficLog.HANDLER_NAME)));
        assertThat(rawTrafficHandlers(handlers), is(empty()));
    }

    @Test
    public void shouldAddNoHandlerToAWebSocketConnectionWhenOff() throws Exception {
        Map<String, ChannelHandler> handlers = exchangeOverSocket(initializer(baseConfiguration().logRawNetworkTraffic(false)), false, WEBSOCKET_UPGRADE, "HTTP/1.1 101");

        assertThat(hasHandler(handlers, io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder.class), is(true));
        assertThat(handlers.keySet(), not(hasItem(RawNetworkTrafficLog.HANDLER_NAME)));
        assertThat(rawTrafficHandlers(handlers), is(empty()));
    }

    @Test
    public void shouldKeepLoggingAWebSocketConnectionAfterTheUpgradeWhenOn() throws Exception {
        Map<String, ChannelHandler> handlers = exchangeOverSocket(capturingInitializer(), false, WEBSOCKET_UPGRADE, "HTTP/1.1 101");

        assertThat(hasHandler(handlers, io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder.class), is(true));
        assertThat(handlers.keySet(), hasItem(RawNetworkTrafficLog.HANDLER_NAME));
        awaitLogged("HTTP/1.1 101");
    }

    @Test
    public void shouldGiveTheHttp2CodecAFrameLoggerOnlyWhenOn() {
        EmbeddedChannel off = connection(initializer(baseConfiguration().logRawNetworkTraffic(false)));
        off.writeInbound(Unpooled.wrappedBuffer(H2C_PREFACE_AND_SETTINGS));
        releaseOutbound(off);
        EmbeddedChannel on = connection(capturingInitializer());
        on.writeInbound(Unpooled.wrappedBuffer(H2C_PREFACE_AND_SETTINGS));
        releaseOutbound(on);

        assertThat(off.pipeline().get(Http2FrameCodec.class).encoder().frameWriter(), is(not(instanceOf(Http2OutboundFrameLogger.class))));
        assertThat(on.pipeline().get(Http2FrameCodec.class).encoder().frameWriter(), is(instanceOf(Http2OutboundFrameLogger.class)));
    }

    private MockServerUnificationInitializer tlsInitializer(RawNetworkTrafficLog rawNetworkTrafficLog) {
        Configuration configuration = baseConfiguration().logRawNetworkTraffic(false);
        NettySslContextFactory sslContextFactory = new NettySslContextFactory(configuration, new MockServerLogger(), true);
        MockServerUnificationInitializer initializer = rawNetworkTrafficLog == null
            ? new MockServerUnificationInitializer(configuration, mock(LifeCycle.class), httpState, actionHandler(), sslContextFactory)
            : new MockServerUnificationInitializer(configuration, mock(LifeCycle.class), httpState, actionHandler(), sslContextFactory, rawNetworkTrafficLog);
        initializers.add(initializer);
        return initializer;
    }

    /**
     * Sends a request on a real connection, waits until the response starts as expected, and returns the server's
     * handlers of the server's side of the connection, taken before the connection closes (which empties it).
     */
    private Map<String, ChannelHandler> exchangeOverSocket(MockServerUnificationInitializer initializer, boolean tls, byte[] request, String expectedResponseStart) throws Exception {
        List<Channel> acceptedChannels = new CopyOnWriteArrayList<>();
        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Channel server = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        acceptedChannels.add(channel);
                        channel.pipeline().addLast(initializer);
                    }
                })
                .bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 0)).sync().channel();
            try (Socket socket = new Socket()) {
                socket.setSoTimeout(20_000);
                socket.connect(server.localAddress(), 10_000);
                Socket connection = tls ? StartTlsUpstream.startTlsAsClient(socket, "localhost", "TLSv1.3", false) : socket;
                OutputStream output = connection.getOutputStream();
                output.write(request);
                output.flush();
                byte[] response = new byte[expectedResponseStart.length()];
                int read = 0;
                while (read < response.length) {
                    int count = connection.getInputStream().read(response, read, response.length - read);
                    assertThat("connection closed before the response", count > 0, is(true));
                    read += count;
                }
                assertThat(new String(response, StandardCharsets.US_ASCII), is(expectedResponseStart));
                Channel accepted = acceptedChannels.get(0);
                // read on the connection's own event loop, where the exchange has finished changing it
                return accepted.eventLoop().submit(() -> new LinkedHashMap<>(accepted.pipeline().toMap())).get(10, TimeUnit.SECONDS);
            } finally {
                server.close().sync();
            }
        } finally {
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
        }
    }

    private Configuration baseConfiguration() {
        return configuration().http2Enabled(true).assumeAllRequestsAreHttp(false);
    }

    private HttpActionHandler actionHandler() {
        HttpActionHandler actionHandler = mock(HttpActionHandler.class);
        when(actionHandler.getHttpClient()).thenReturn(httpClient);
        return actionHandler;
    }

    private MockServerUnificationInitializer initializer(Configuration configuration) {
        MockServerUnificationInitializer initializer = new MockServerUnificationInitializer(configuration, mock(LifeCycle.class), httpState, actionHandler(), null);
        initializers.add(initializer);
        return initializer;
    }

    private MockServerUnificationInitializer capturingInitializer() {
        MockServerUnificationInitializer initializer = new MockServerUnificationInitializer(baseConfiguration(), mock(LifeCycle.class), httpState, actionHandler(), null,
            new RawNetworkTrafficLog(RawNetworkTrafficLog.SERVER_SIDE, () -> null, logged::add));
        initializers.add(initializer);
        return initializer;
    }

    private EmbeddedChannel connection(MockServerUnificationInitializer initializer) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(REMOTE_SOCKET).set(new InetSocketAddress("127.0.0.1", 1234));
        channel.pipeline().addLast(initializer);
        channels.add(channel);
        return channel;
    }

    private static List<ChannelHandler> rawTrafficHandlers(EmbeddedChannel channel) {
        return rawTrafficHandlers(channel.pipeline().toMap());
    }

    private static boolean hasHandler(Map<String, ChannelHandler> handlers, Class<?> type) {
        return handlers.values().stream().anyMatch(type::isInstance);
    }

    private static List<ChannelHandler> rawTrafficHandlers(Map<String, ChannelHandler> handlers) {
        List<ChannelHandler> found = new ArrayList<>();
        for (Map.Entry<String, ChannelHandler> entry : handlers.entrySet()) {
            if (entry.getValue().getClass().getName().startsWith("org.mockserver.logging.RawNetworkTraffic")) {
                found.add(entry.getValue());
            }
        }
        return found;
    }

    private static void releaseOutbound(EmbeddedChannel channel) {
        Object outbound;
        while ((outbound = channel.readOutbound()) != null) {
            if (outbound instanceof ByteBuf) {
                ((ByteBuf) outbound).release();
            }
        }
    }

    private void awaitLogged(String text) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (logged.stream().noneMatch(entry -> entry.contains(text)) && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(5);
        }
        assertThat(String.join("\n", logged), containsString(text));
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }
}
