package org.mockserver.netty.http3;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicConnectionCloseEvent;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpError;
import org.mockserver.testing.socket.Ipv4DatagramChannelFactory;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockserver.configuration.Configuration.configuration;

/**
 * Integration test proving that {@link Http3ResponseWriter#writeStreamError(long)} (the HTTP/3 leg
 * of the {@link HttpError} stream-error action) resets the QUIC request stream rather than returning
 * a normal response. Uses an in-JVM Netty QUIC client/server.
 * <p>
 * Skips gracefully when the native QUIC transport (BoringSSL) is not available.
 */
@SuppressWarnings("deprecation") // NioEventLoopGroup deprecation in Netty 4.2
public class Http3StreamErrorIntegrationTest {

    private static final Configuration CONFIGURATION = configuration();
    private static final MockServerLogger LOGGER = new MockServerLogger(Http3StreamErrorIntegrationTest.class);

    private Channel serverChannel;
    private NioEventLoopGroup serverGroup;
    private NioEventLoopGroup clientGroup;

    @Before
    public void setUp() {
        assumeQuicAvailable();
    }

    @After
    public void tearDown() {
        if (serverChannel != null) {
            try { serverChannel.close().sync(); } catch (Exception ignored) {}
            serverChannel = null;
        }
        if (serverGroup != null) {
            serverGroup.shutdownGracefully();
            serverGroup = null;
        }
        if (clientGroup != null) {
            clientGroup.shutdownGracefully();
            clientGroup = null;
        }
    }

    @Test
    public void shouldResetHttp3StreamInsteadOfResponding() throws Exception {
        // given - a QUIC/HTTP3 server whose handler resets the stream with H3_REQUEST_CANCELLED
        long errorCode = HttpError.StreamErrorCode.H3_REQUEST_CANCELLED.code();
        int port = startServer(writer -> writer.writeStreamError(errorCode));

        // when - send an HTTP/3 request and observe the stream outcome
        StreamOutcome outcome = sendHttp3Request(port, "/reset");

        // then - the client receives NO response headers/body and the stream is reset (not completed
        // with a normal 200). The QUIC stack surfaces the peer RESET_STREAM as a stream exception
        // and/or an immediate input-close without any HEADERS frame.
        assertThat("no response HEADERS frame should be received when the stream is reset",
            outcome.receivedHeaders, is(false));
        assertThat("the stream should have been reset (exception) or closed without a response",
            outcome.resetOrClosedWithoutResponse(), is(true));
    }

    @Test
    public void shouldCloseQuicConnectionWithH3InternalErrorOnConnectionReset() throws Exception {
        // given - a server whose handler applies the HttpError resetConnection seam
        int port = startServer(Http3ResponseWriter::writeConnectionReset);

        // when
        CompletableFuture<QuicConnectionCloseEvent> closeEvent = new CompletableFuture<>();
        StreamOutcome outcome = sendHttp3Request(port, "/reset", closeEvent);

        // then - QUIC has no TCP reset, so the whole connection is closed by the peer with H3_INTERNAL_ERROR
        QuicConnectionCloseEvent event = closeEvent.getNow(null);
        assertThat("the client should see the peer close the QUIC connection", event, notNullValue());
        assertThat(event.isApplicationClose(), is(true));
        assertThat((long) event.error(), is(HttpError.StreamErrorCode.H3_INTERNAL_ERROR.code()));
        assertThat(outcome.receivedHeaders, is(false));
    }

    // ---- server ----

    private int startServer(Consumer<Http3ResponseWriter> action) throws Exception {
        serverGroup = new NioEventLoopGroup(1);

        KeyPair keyPair = generateKeyPair();
        X509Certificate cert = generateSelfSignedCert(keyPair);

        QuicSslContext sslContext = QuicSslContextBuilder
            .forServer(keyPair.getPrivate(), null, cert)
            .applicationProtocols(Http3.supportedApplicationProtocols())
            .build();

        serverChannel = new Bootstrap()
            .group(serverGroup)
            .channelFactory(Ipv4DatagramChannelFactory.INSTANCE)
            .handler(Http3.newQuicServerCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(30000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                .handler(new Http3ServerConnectionHandler(
                    new ChannelInitializer<QuicStreamChannel>() {
                        @Override
                        protected void initChannel(QuicStreamChannel ch) {
                            ch.pipeline().addLast(new StreamErrorTestHandler(action));
                        }
                    }
                ))
                .build())
            .bind(new InetSocketAddress(0))
            .sync()
            .channel();

        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    /**
     * Server handler that, on receiving the request headers, applies a production {@link Http3ResponseWriter}
     * seam ({@code writeStreamError} or {@code writeConnectionReset}).
     */
    private static class StreamErrorTestHandler extends Http3RequestStreamInboundHandler {
        private final Consumer<Http3ResponseWriter> action;

        StreamErrorTestHandler(Consumer<Http3ResponseWriter> action) {
            this.action = action;
        }

        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame headersFrame) {
            action.accept(new Http3ResponseWriter(CONFIGURATION, LOGGER, ctx));
        }

        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame dataFrame) {
            dataFrame.release();
        }

        @Override
        protected void channelInputClosed(ChannelHandlerContext ctx) {
            // nothing to do
        }
    }

    // ---- client ----

    private static class StreamOutcome {
        volatile boolean receivedHeaders = false;
        volatile boolean exceptionRaised = false;
        volatile boolean inputClosed = false;

        boolean resetOrClosedWithoutResponse() {
            return !receivedHeaders && (exceptionRaised || inputClosed);
        }
    }

    private StreamOutcome sendHttp3Request(int port, String path) throws Exception {
        return sendHttp3Request(port, path, null);
    }

    private StreamOutcome sendHttp3Request(int port, String path, CompletableFuture<QuicConnectionCloseEvent> closeEvent) throws Exception {
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
            .handler(new ChannelInitializer<QuicChannel>() {
                @Override
                protected void initChannel(QuicChannel ch) {
                    ch.pipeline().addLast(new Http3ClientConnectionHandler());
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            if (closeEvent != null && evt instanceof QuicConnectionCloseEvent) {
                                closeEvent.complete((QuicConnectionCloseEvent) evt);
                            }
                            ctx.fireUserEventTriggered(evt);
                        }
                    });
                }
            })
            .remoteAddress(new InetSocketAddress("127.0.0.1", port))
            .connect()
            .get(15, TimeUnit.SECONDS);

        StreamOutcome outcome = new StreamOutcome();
        CountDownLatch done = new CountDownLatch(1);

        QuicStreamChannel requestStream = Http3.newRequestStream(
            quicChannel,
            new Http3RequestStreamInboundHandler() {
                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame headersFrame) {
                    outcome.receivedHeaders = true;
                }

                @Override
                protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame dataFrame) {
                    ByteBuf content = dataFrame.content();
                    content.release();
                }

                @Override
                protected void channelInputClosed(ChannelHandlerContext ctx) {
                    outcome.inputClosed = true;
                    done.countDown();
                    ctx.close();
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    outcome.exceptionRaised = true;
                    done.countDown();
                }

                @Override
                public void channelInactive(ChannelHandlerContext ctx) {
                    done.countDown();
                }
            }
        ).sync().getNow();

        DefaultHttp3HeadersFrame requestHeaders = new DefaultHttp3HeadersFrame();
        requestHeaders.headers().method("GET");
        requestHeaders.headers().path(path);
        requestHeaders.headers().scheme("https");
        requestHeaders.headers().authority("127.0.0.1:" + port);

        requestStream.writeAndFlush(requestHeaders)
            .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);

        done.await(5, TimeUnit.SECONDS);

        if (closeEvent != null) {
            // wait for the peer's close before closing locally, which would end the wait for it
            closeEvent.completeOnTimeout(null, 5, TimeUnit.SECONDS).join();
        }
        quicChannel.close().sync();
        clientChannel.close().sync();

        return outcome;
    }

    // ---- crypto helpers ----

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(256, new SecureRandom());
        return gen.generateKeyPair();
    }

    private static X509Certificate generateSelfSignedCert(KeyPair keyPair) throws Exception {
        org.bouncycastle.asn1.x500.X500Name issuer =
            new org.bouncycastle.asn1.x500.X500Name("CN=MockServer HTTP/3 Test, O=MockServer");
        java.math.BigInteger serial = new java.math.BigInteger(64, new SecureRandom());
        java.util.Date notBefore = new java.util.Date();
        java.util.Date notAfter = new java.util.Date(notBefore.getTime() + TimeUnit.DAYS.toMillis(1));

        org.bouncycastle.operator.ContentSigner signer =
            new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withECDSA")
                .build(keyPair.getPrivate());
        org.bouncycastle.cert.X509CertificateHolder holder =
            new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, issuer, keyPair.getPublic()
            ).build(signer);

        return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(holder);
    }

    // ---- assume / trust helpers ----

    private static void assumeQuicAvailable() {
        try {
            Assume.assumeTrue(
                "native QUIC transport not available on this platform -- skipping HTTP/3 stream-error test",
                io.netty.handler.codec.quic.Quic.isAvailable()
            );
        } catch (Throwable t) {
            Assume.assumeNoException(
                "native QUIC transport failed to load -- skipping HTTP/3 stream-error test", t
            );
        }
    }
}
