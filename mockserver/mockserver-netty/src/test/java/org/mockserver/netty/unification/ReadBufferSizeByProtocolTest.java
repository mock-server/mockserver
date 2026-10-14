package org.mockserver.netty.unification;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.ssl.SslHandler;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.action.http.HttpActionHandler;
import org.mockserver.netty.MockServerUnificationInitializer;
import org.mockserver.netty.proxy.relay.RelayUpstreamStandIn;
import org.mockserver.netty.proxy.BinaryRequestProxyingHandler;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.tls.KeyStoreFactory;
import org.mockserver.socket.tls.NettySslContextFactory;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static org.mockserver.netty.unification.BinaryAwareRecvByteBufAllocatorTest.readsOf;

/**
 * How the reads of a real connection are sized once MockServer knows what it carries, with a real TLS client:
 * HTTP and HTTP/2 inside TLS as Netty sizes them, binary at 64 KiB whether TLS came first or part way through.
 */
public class ReadBufferSizeByProtocolTest {

    private static final int[] BYTES_WAITING = {5, 5, 5, 5, 5, 5, 5, 5, 100, 422, 800, 3000, 200_000, 5, 5, 5, 5, 1200};
    // enough full reads to take any adaptive allocator to its largest buffer, wherever it had got to
    private static final int[] UNTIL_LARGEST = {4_000_000, 4_000_000};
    private static final byte[] BINARY_MESSAGE = {0, 0, 0, 8, 4, (byte) 0xd2, 0x16, 0x2f};

    private static HttpState httpState;

    private final Configuration configuration = configuration().http2Enabled(true).assumeAllRequestsAreHttp(false);
    private final NettyHttpClient httpClient = mock(NettyHttpClient.class);
    // binary connections are relayed to stand-ins; one case below relays to a real upstream
    private final RelayUpstreamStandIn upstreams = new RelayUpstreamStandIn(httpClient);
    private final AtomicReference<Channel> connection = new AtomicReference<>();
    private final List<Socket> sockets = new ArrayList<>();
    private MockServerUnificationInitializer initializer;
    private EventLoopGroup group;
    private Channel server;

    @BeforeClass
    public static void startServerState() {
        httpState = new HttpState(configuration(), new MockServerLogger(), mock(Scheduler.class));
    }

    @AfterClass
    public static void stopServerState() {
        httpState.stop();
        httpState = null;
    }

    @Before
    public void startServer() throws Exception {
        HttpActionHandler actionHandler = mock(HttpActionHandler.class);
        when(actionHandler.getHttpClient()).thenReturn(httpClient);
        initializer = new MockServerUnificationInitializer(configuration, mock(LifeCycle.class), httpState, actionHandler, new NettySslContextFactory(configuration, new MockServerLogger(), true));
        group = new NioEventLoopGroup(1);
        server = new ServerBootstrap()
            .group(group)
            .channel(NioServerSocketChannel.class)
            // what is not HTTP is relayed, to an upstream that never answers, so the connection stays open
            .childAttr(REMOTE_SOCKET, new InetSocketAddress("127.0.0.1", 1234))
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) {
                    connection.set(channel);
                    channel.pipeline().addLast(initializer);
                }
            })
            .bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 0)).sync().channel();
    }

    @After
    public void stopServer() throws Exception {
        for (Socket socket : sockets) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing what a test may already have closed
            }
        }
        server.close().sync();
        initializer.getMcpSessionManager().shutdown();
        group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
        upstreams.releaseAll();
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket();
        sockets.add(socket);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(20_000);
        socket.connect(server.localAddress(), 10_000);
        return socket;
    }

    private SSLSocket startTls(Socket socket, String... applicationProtocols) throws IOException {
        SSLSocket tls = (SSLSocket) new KeyStoreFactory(configuration(), new MockServerLogger()).sslContext().getSocketFactory()
            .createSocket(socket, "127.0.0.1", socket.getPort(), true);
        sockets.add(tls);
        tls.setUseClientMode(true);
        if (applicationProtocols.length > 0) {
            SSLParameters parameters = tls.getSSLParameters();
            parameters.setApplicationProtocols(applicationProtocols);
            tls.setSSLParameters(parameters);
        }
        tls.startHandshake();
        return tls;
    }

    private static void send(Socket socket, byte[] bytes) throws IOException {
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
    }

    private <T> T onEventLoop(Callable<T> read) throws Exception {
        return connection.get().eventLoop().submit(read).get(10, TimeUnit.SECONDS);
    }

    private void awaitHandler(Class<? extends ChannelHandler> handler) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (connection.get() != null && onEventLoop(() -> connection.get().pipeline().get(handler) != null)) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(5);
        }
        throw new AssertionError("the connection was not given a " + handler.getSimpleName() + " within 20 seconds");
    }

    /** How the connection's reads are sized from now on, by the handle its own read loop uses. */
    private List<String> readsFromNowOn(int[]... readLoops) throws Exception {
        return onEventLoop(() -> {
            Channel channel = connection.get();
            @SuppressWarnings("deprecation")
            RecvByteBufAllocator.Handle handle = channel.unsafe().recvBufAllocHandle();
            List<String> reads = new ArrayList<>();
            for (int[] bytesWaiting : readLoops) {
                reads = readsOf(handle, channel.config(), bytesWaiting);
            }
            return reads;
        });
    }

    /** How Netty's adaptive allocator sizes the same reads, from the same starting point: its largest buffer. */
    @SuppressWarnings("deprecation")
    private List<String> readsAsNettySizesThem() throws Exception {
        return onEventLoop(() -> {
            AdaptiveRecvByteBufAllocator allocator = new AdaptiveRecvByteBufAllocator();
            allocator.maxMessagesPerRead(connection.get().config().getOption(ChannelOption.MAX_MESSAGES_PER_READ));
            RecvByteBufAllocator.Handle handle = allocator.newHandle();
            readsOf(handle, connection.get().config(), UNTIL_LARGEST);
            return readsOf(handle, connection.get().config(), BYTES_WAITING);
        });
    }

    private void assertReadsAreSizedAsNettySizesThem() throws Exception {
        List<String> reads = readsFromNowOn(UNTIL_LARGEST, BYTES_WAITING);

        assertThat(reads, is(readsAsNettySizesThem()));
        assertThat("and those sizes follow the traffic down", reads, hasItem("guess 32768 buffer 32768"));
    }

    private void assertEveryReadIsGiven64KiB() throws Exception {
        List<String> reads = readsFromNowOn(BYTES_WAITING);
        reads.removeIf(read -> read.startsWith("end of loop"));

        assertThat(reads, everyItem(is("guess 65536 buffer 65536")));
    }

    @Test
    public void shouldGiveEveryReadOfABinaryConnection64KiBWhenItIsRelayedToARealUpstream() throws Exception {
        try (ServerSocket upstream = new ServerSocket(0, 1, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}))) {
            InetSocketAddress upstreamAddress = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), upstream.getLocalPort());
            NettyHttpClient direct = new NettyHttpClient(configuration, new MockServerLogger(), (EventLoopGroup) null, null, false);
            when(httpClient.connectBinaryRelay(any(EventLoop.class), any(InetSocketAddress.class), anyBoolean(), any(ChannelHandler.class)))
                .thenAnswer(invocation -> direct.connectBinaryRelay(invocation.getArgument(0), upstreamAddress, invocation.getArgument(2), invocation.getArgument(3)));
            Socket socket = connect();
            send(socket, BINARY_MESSAGE);
            awaitHandler(BinaryRequestProxyingHandler.class);
            upstream.setSoTimeout(20_000);
            try (Socket relayed = upstream.accept()) {
                relayed.setSoTimeout(20_000);
                assertThat("the message went on the one upstream connection", relayed.getInputStream().readNBytes(BINARY_MESSAGE.length), is(BINARY_MESSAGE));

                assertEveryReadIsGiven64KiB();
            }
        }
    }

    @Test
    public void shouldSizeTheReadsOfHttpAsNettyDoes() throws Exception {
        send(connect(), "GET /some/path HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
        awaitHandler(HttpServerCodec.class);

        assertReadsAreSizedAsNettySizesThem();
    }

    @Test
    public void shouldSizeTheReadsOfHttpInsideTlsAsNettyDoes() throws Exception {
        SSLSocket tls = startTls(connect());
        send(tls, "GET /some/path HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
        awaitHandler(HttpServerCodec.class);

        assertThat(onEventLoop(() -> connection.get().pipeline().get(SslHandler.class)), is(notNullValue()));
        assertReadsAreSizedAsNettySizesThem();
    }

    @Test
    public void shouldSizeTheReadsOfHttp2InsideTlsAsNettyDoes() throws Exception {
        SSLSocket tls = startTls(connect(), "h2");
        assertThat(tls.getApplicationProtocol(), is("h2"));
        send(tls, "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        awaitHandler(Http2FrameCodec.class);

        assertReadsAreSizedAsNettySizesThem();
    }

    @Test
    public void shouldGiveEveryReadOfABinaryConnectionThatStartedWithTls64KiB() throws Exception {
        SSLSocket tls = startTls(connect());
        send(tls, BINARY_MESSAGE);
        awaitHandler(BinaryRequestProxyingHandler.class);

        assertThat(onEventLoop(() -> connection.get().pipeline().get(SslHandler.class)), is(notNullValue()));
        assertEveryReadIsGiven64KiB();
    }

    @Test
    public void shouldGiveEveryReadOfABinaryConnection64KiBBeforeAndAfterItTurnsOnTls() throws Exception {
        Socket socket = connect();
        send(socket, BINARY_MESSAGE);
        awaitHandler(BinaryRequestProxyingHandler.class);
        assertEveryReadIsGiven64KiB();

        SSLSocket tls = startTls(socket);
        send(tls, BINARY_MESSAGE);
        awaitHandler(SslHandler.class);

        assertEveryReadIsGiven64KiB();
    }
}
