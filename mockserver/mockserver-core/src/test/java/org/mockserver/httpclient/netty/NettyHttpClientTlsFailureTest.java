package org.mockserver.httpclient.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandshakeTimeoutException;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.httpclient.SocketConnectionException;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;

import javax.net.ssl.SSLHandshakeException;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * A request over TLS whose handshake with the upstream fails, failing with the handshake's own exception as the
 * cause of the {@link SocketConnectionException} it failed with before, in place of the teardown that follows it
 * ("Channel handler removed before valid response has been received"). Each upstream listens on 127.0.0.1.
 */
public class NettyHttpClientTlsFailureTest {

    private static final MockServerLogger mockServerLogger = new MockServerLogger(NettyHttpClientTlsFailureTest.class);
    private static EventLoopGroup clientEventLoopGroup;
    private static EventLoopGroup upstreamEventLoopGroup;
    private static SelfSignedCertificate certificate;
    private String host;
    private static Channel tlsUpstream;
    private static RawUpstream plainHttpUpstream;
    private static RawUpstream silentUpstream;
    private static RawUpstream closingUpstream;

    @BeforeClass
    public static void startUpstreams() throws Exception {
        clientEventLoopGroup = new NioEventLoopGroup(2, new Scheduler.SchedulerThreadFactory(NettyHttpClientTlsFailureTest.class.getSimpleName() + "-client"));
        upstreamEventLoopGroup = new NioEventLoopGroup(1, new Scheduler.SchedulerThreadFactory(NettyHttpClientTlsFailureTest.class.getSimpleName() + "-upstream"));
        certificate = new SelfSignedCertificate("upstream.example");
        SslContext serverContext = SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey()).build();
        tlsUpstream = new ServerBootstrap()
            .group(upstreamEventLoopGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(serverContext.newHandler(ch.alloc()));
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                        }
                    });
                }
            })
            .bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();
        plainHttpUpstream = new RawUpstream(socket -> {
            socket.getInputStream().read();
            socket.getOutputStream().write("HTTP/1.1 400 Bad Request\r\ncontent-length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            drain(socket.getInputStream());
        });
        silentUpstream = new RawUpstream(socket -> drain(socket.getInputStream()));
        // reads the whole ClientHello first: a socket closed with bytes unread is reset, not closed
        closingUpstream = new RawUpstream(NettyHttpClientTlsFailureTest::readTlsRecord);
    }

    @AfterClass
    public static void stopUpstreams() throws Exception {
        tlsUpstream.close().sync();
        plainHttpUpstream.close();
        silentUpstream.close();
        closingUpstream.close();
        certificate.delete();
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
        upstreamEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Test
    public void shouldFailWithAnUntrustedCertificate() {
        Throwable failure = failureOf(configuration().forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.JVM), port(tlsUpstream));

        assertThat(failure.getCause(), instanceOf(SSLHandshakeException.class));
        assertThat(failure.getMessage(), startsWith(failedHandshakeWith(port(tlsUpstream)) + "SSLHandshakeException: "));
        // with the JDK's TLS provider in its message, with OpenSSL's in its cause
        assertThat(failure.getMessage(), containsString("unable to find valid certification path to requested target"));
    }

    @Test
    public void shouldFailWithACertificateForAnotherHost() {
        Configuration trustingTheUpstreamsCertificate = configuration()
            .forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.CUSTOM)
            .forwardProxyTLSCustomTrustX509Certificates(certificate.certificate().getAbsolutePath());

        Throwable failure = failureOf(trustingTheUpstreamsCertificate, port(tlsUpstream));

        assertThat(failure.getCause(), instanceOf(SSLHandshakeException.class));
        assertThat(failure.getMessage(), startsWith(failedHandshakeWith(port(tlsUpstream)) + "SSLHandshakeException: "));
        // which, as the JDK words it, depends on whether the TLS handler was given a name or an address
        assertThat(failure.getMessage(), matchesPattern(".*No (name matching|subject alternative names).*"));
    }

    @Test
    public void shouldFailWithAnUpstreamThatAnswersInPlainHttp() {
        Throwable failure = failureOf(configuration(), plainHttpUpstream.port());

        assertThat(failure.getCause(), instanceOf(NotSslRecordException.class));
        assertThat(failure.getMessage(), startsWith(failedHandshakeWith(plainHttpUpstream.port()) + "NotSslRecordException: not an SSL/TLS record"));
        assertThat("the upstream's bytes, as a hex dump", failure.getMessage(), not(containsString("485454502f")));
    }

    @Test
    public void shouldFailWithAHandshakeThatTimesOut() {
        Throwable failure = failureOf(configuration().socketConnectionTimeoutInMillis(500L), silentUpstream.port());

        assertThat(failure.getCause(), instanceOf(SslHandshakeTimeoutException.class));
        assertThat(failure.getMessage(), is(failedHandshakeWith(silentUpstream.port()) + "SslHandshakeTimeoutException: handshake timed out after 500ms"));
    }

    @Test
    public void shouldFailWithAnUpstreamThatClosesTheConnectionDuringTheHandshake() {
        Throwable failure = failureOf(configuration(), closingUpstream.port());

        assertThat(failure.getCause(), instanceOf(SSLHandshakeException.class));
        assertThat(failure.getMessage(), is(failedHandshakeWith(closingUpstream.port()) + "SSLHandshakeException: upstream closed the connection during the TLS handshake"));
    }

    private Throwable failureOf(Configuration configuration, int upstreamPort) {
        NettyHttpClient client = new NettyHttpClient(configuration, mockServerLogger, clientEventLoopGroup, null, true);
        return handshakeFailure(upstreamPort, () -> client
            .sendRequest(request().withSecure(true).withHeader(HOST.toString(), "127.0.0.1:" + upstreamPort))
            .get(30, TimeUnit.SECONDS));
    }

    private Throwable handshakeFailure(int upstreamPort, ThrowingRunnable forward) {
        ExecutionException exception = assertThrows(ExecutionException.class, forward);
        // the type it failed with before, so callers that catch it still do
        assertThat(exception.getCause(), instanceOf(SocketConnectionException.class));
        Matcher matcher = Pattern.compile("^TLS handshake with ([^ ]+):" + upstreamPort + " failed: ").matcher(exception.getCause().getMessage());
        assertThat(exception.getCause().getMessage(), matcher.find(), is(true));
        host = matcher.group(1);
        return exception.getCause();
    }

    /**
     * The start of the message: the host is as the TLS handler saw it, which may be the name 127.0.0.1 resolves to.
     */
    private String failedHandshakeWith(int upstreamPort) {
        return "TLS handshake with " + host + ":" + upstreamPort + " failed: ";
    }

    private static int port(Channel listener) {
        return ((InetSocketAddress) listener.localAddress()).getPort();
    }

    private static void readTlsRecord(Socket socket) throws IOException {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        byte[] header = new byte[5];
        input.readFully(header);
        input.readFully(new byte[((header[3] & 0xff) << 8) | (header[4] & 0xff)]);
    }

    private static void drain(InputStream input) throws IOException {
        byte[] buffer = new byte[1024];
        while (input.read(buffer) != -1) {
            continue;
        }
    }

    /**
     * Accepts connections on 127.0.0.1 one at a time and hands each to {@code serve}, then closes it.
     */
    private static final class RawUpstream {
        private final ServerSocket listener = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));

        private RawUpstream(Serving serve) throws IOException {
            Thread acceptor = new Thread(() -> {
                while (!listener.isClosed()) {
                    try (Socket socket = listener.accept()) {
                        socket.setSoTimeout(30_000);
                        serve.serve(socket);
                    } catch (IOException closedOrFailed) {
                        // the next connection, or the end of the loop
                    }
                }
            }, NettyHttpClientTlsFailureTest.class.getSimpleName() + "-rawUpstream");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        private int port() {
            return listener.getLocalPort();
        }

        private void close() throws IOException {
            listener.close();
        }
    }

    private interface Serving {
        void serve(Socket socket) throws IOException;
    }
}
