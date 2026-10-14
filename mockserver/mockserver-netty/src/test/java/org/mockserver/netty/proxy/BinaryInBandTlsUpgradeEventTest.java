package org.mockserver.netty.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.junit.AfterClass;
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
import org.mockserver.netty.integration.proxy.direct.StartTlsUpstream;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.tls.NettySslContextFactory;

import javax.net.ssl.SSLSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static org.mockserver.netty.integration.proxy.direct.StartTlsUpstream.SSL_REQUEST;
import static org.mockserver.netty.unification.PortUnificationHandler.isSslEnabledUpstream;

/**
 * Where a binary connection learns that its client has started TLS part way through: in MockServer's own pipeline,
 * the {@link SniCompletionEvent} that Netty fires once the server certificate is chosen reaches the handler that
 * forwards binary messages, after the message sent in the clear and before the first one decrypted. A connection
 * that opens with TLS gets that event before the binary handler exists. The handlers are MockServer's; only the
 * upstream connection is a stand-in, which never answers.
 */
public class BinaryInBandTlsUpgradeEventTest {

    private static final byte[] STARTUP = {0, 0, 0, 23, 0, 3, 0, 0, 'u', 's', 'e', 'r', 0, 'p', 'o', 's', 't', 'g', 'r', 'e', 's', 0, 0};

    private static HttpState httpState;

    private final Configuration configuration = configuration();
    private final List<String> seen = new CopyOnWriteArrayList<>();
    private final List<String> seenAfterBinaryHandler = new CopyOnWriteArrayList<>();
    private final List<String> order = new CopyOnWriteArrayList<>();

    @BeforeClass
    public static void startServerState() {
        httpState = new HttpState(configuration(), new MockServerLogger(), mock(Scheduler.class));
    }

    @AfterClass
    public static void stopServerState() {
        httpState.stop();
        httpState = null;
    }

    @Test
    public void shouldTellTheBinaryHandlerWhenTheClientStartsTlsPartWayThrough() throws Exception {
        withMockServerPipeline(server -> {
            try (Socket client = connect(server)) {
                client.getOutputStream().write(SSL_REQUEST);
                client.getOutputStream().flush();
                awaitSeen("read " + ByteBufUtil.hexDump(SSL_REQUEST));

                // the stand-in upstream never answers S, and need not: the client starts TLS regardless
                SSLSocket tls = StartTlsUpstream.startTlsAsClient(client, "localhost", "TLSv1.3", false);
                tls.getOutputStream().write(STARTUP);
                tls.getOutputStream().flush();
                awaitSeen("read " + ByteBufUtil.hexDump(STARTUP));
            }
        });

        String sniEvent = "sni success=true sslHandler=true tlsUpstream=true handshakeDone=false";
        assertThat(seenAfterBinaryHandler, hasItem(sniEvent));
        assertThat("once, and the only SNI event", seenAfterBinaryHandler.stream().filter(event -> event.startsWith("sni")).count(), is(1L));
        assertThat(seenAfterBinaryHandler, hasItem("handshake success=true"));
        // in order on one event loop: the message in the clear, the event, the first decrypted message
        assertThat(merged(), contains(
            "read " + ByteBufUtil.hexDump(SSL_REQUEST),
            sniEvent,
            "read " + ByteBufUtil.hexDump(STARTUP)
        ));
    }

    @Test
    public void shouldNotTellTheBinaryHandlerOfTlsAConnectionOpenedWith() throws Exception {
        withMockServerPipeline(server -> {
            try (Socket client = connect(server)) {
                SSLSocket tls = StartTlsUpstream.startTlsAsClient(client, "localhost", "TLSv1.3", false);
                tls.getOutputStream().write(STARTUP);
                tls.getOutputStream().flush();
                awaitSeen("read " + ByteBufUtil.hexDump(STARTUP));
            }
        });

        // the connection is known to be TLS before it is known to be binary
        assertThat(seen, hasItem("sni success=true sslHandler=true tlsUpstream=true handshakeDone=false"));
        assertThat(seenAfterBinaryHandler, is(empty()));
    }

    /** What was seen before the binary handler (reads) and after it (events), in the order seen. */
    private List<String> merged() {
        List<String> merged = new CopyOnWriteArrayList<>();
        for (String event : order) {
            if (event.startsWith("read") || event.startsWith("after ")) {
                merged.add(event.startsWith("after ") ? event.substring("after ".length()) : event);
            }
        }
        merged.removeIf(event -> event.startsWith("handshake"));
        return merged;
    }

    private void record(List<String> list, String prefix, String event) {
        list.add(event);
        order.add(prefix + event);
    }

    private static String describe(ChannelHandlerContext ctx, Object event) {
        if (event instanceof SniCompletionEvent) {
            SniCompletionEvent sni = (SniCompletionEvent) event;
            SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
            return "sni success=" + sni.isSuccess()
                + " sslHandler=" + (sslHandler != null)
                + " tlsUpstream=" + isSslEnabledUpstream(ctx.channel())
                + " handshakeDone=" + (sslHandler != null && sslHandler.handshakeFuture().isDone());
        } else if (event instanceof SslHandshakeCompletionEvent) {
            return "handshake success=" + ((SslHandshakeCompletionEvent) event).isSuccess();
        }
        return null;
    }

    private Socket connect(Channel server) throws Exception {
        Socket client = new Socket();
        client.setTcpNoDelay(true);
        client.setSoTimeout(20_000);
        client.connect(server.localAddress(), 10_000);
        return client;
    }

    private void awaitSeen(String event) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!seen.contains(event) && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(1);
        }
        assertThat("seen within 20 seconds: " + event, seen, hasItem(event));
    }

    private interface WithServer {
        void run(Channel server) throws Exception;
    }

    /**
     * MockServer's pipeline for a port-forwarding connection, with one handler watching what reaches the binary
     * handler and one, added once the binary handler is, watching what the binary handler passes on.
     */
    private void withMockServerPipeline(WithServer test) throws Exception {
        // the event's route is what is tested, so the relay's upstream connections are stand-ins
        NettyHttpClient httpClient = mock(NettyHttpClient.class);
        RelayUpstreamStandIn upstreams = new RelayUpstreamStandIn(httpClient);
        HttpActionHandler actionHandler = mock(HttpActionHandler.class);
        when(actionHandler.getHttpClient()).thenReturn(httpClient);
        MockServerUnificationInitializer initializer = new MockServerUnificationInitializer(
            configuration, mock(LifeCycle.class), httpState, actionHandler, new NettySslContextFactory(configuration, new MockServerLogger(), true)
        );
        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Channel server = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                .childAttr(REMOTE_SOCKET, new InetSocketAddress("127.0.0.1", 1234))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(initializer);
                        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object message) {
                                if (message instanceof ByteBuf) {
                                    record(seen, "", "read " + ByteBufUtil.hexDump((ByteBuf) message));
                                    watchAfterBinaryHandler(ctx);
                                }
                                ctx.fireChannelRead(message);
                            }

                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
                                String description = describe(ctx, event);
                                if (description != null) {
                                    record(seen, "", description);
                                }
                                super.userEventTriggered(ctx, event);
                            }
                        });
                    }
                })
                .bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 0)).sync().channel();
            test.run(server);
            server.close().sync();
        } finally {
            initializer.getMcpSessionManager().shutdown();
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
            upstreams.releaseAll();
        }
    }

    private void watchAfterBinaryHandler(ChannelHandlerContext ctx) {
        ChannelHandlerContext binaryHandler = ctx.pipeline().context(BinaryRequestProxyingHandler.class);
        if (binaryHandler != null && ctx.pipeline().get("after-binary-handler") == null) {
            ctx.pipeline().addAfter(binaryHandler.name(), "after-binary-handler", new ChannelInboundHandlerAdapter() {
                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
                    String description = describe(ctx, event);
                    if (description != null) {
                        record(seenAfterBinaryHandler, "after ", description);
                    }
                    super.userEventTriggered(ctx, event);
                }
            });
        }
    }
}
