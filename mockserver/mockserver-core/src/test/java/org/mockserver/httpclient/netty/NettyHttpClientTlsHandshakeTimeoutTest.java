package org.mockserver.httpclient.netty;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.PortFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Proves that a forward/proxy client TLS handshake to an upstream is bounded by MockServer's configured
 * connection timeout ({@code socketConnectionTimeoutInMillis}) rather than by Netty's fixed default
 * {@code SslHandler} handshake timeout of 10,000ms.
 * <p>
 * The mechanism is exercised deterministically (no reliance on machine load): a raw {@link ServerSocket}
 * accepts the TCP connection but never answers the TLS ClientHello, so the handshake stalls forever and
 * only the handshake timeout can end it. Before the fix the handshake timeout is a hard-coded 10s
 * regardless of configuration; after it, a small configured connection timeout ends the handshake at
 * roughly that bound. The {@code shouldBoundTlsHandshakeByConfiguredConnectionTimeout} test is the
 * negative control: revert the fix in {@code HttpClientInitializer} and it goes red because the handshake
 * then runs for ~10s and breaches the assertion ceiling.
 * <p>
 * This test uses per-instance {@link Configuration} objects only (never the global
 * {@code ConfigurationProperties}), so it mutates no shared state and needs no sequential registration.
 */
public class NettyHttpClientTlsHandshakeTimeoutTest {

    private static EventLoopGroup clientEventLoopGroup;
    private final MockServerLogger mockServerLogger = new MockServerLogger();

    @BeforeClass
    public static void startEventLoopGroup() {
        clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(NettyHttpClientTlsHandshakeTimeoutTest.class.getSimpleName() + "-eventLoop"));
    }

    @AfterClass
    public static void stopEventLoopGroup() {
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    /**
     * Accepts TCP connections and then withholds all bytes, so a TLS client's handshake never receives a
     * ServerHello and can only be ended by the handshake timeout. Accepted sockets are retained so they
     * are not closed by GC, which would otherwise let the client fail fast on connection reset.
     */
    private static final class StalledTlsServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread acceptThread;
        private final List<Socket> accepted = new ArrayList<>();
        private volatile boolean running = true;

        private StalledTlsServer() throws IOException {
            this.serverSocket = new ServerSocket(PortFactory.findFreePort());
            this.acceptThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        synchronized (accepted) {
                            accepted.add(socket);
                        }
                    } catch (IOException e) {
                        return;
                    }
                }
            }, "stalled-tls-server-accept");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();
        }

        private int getPort() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() {
            running = false;
            try {
                serverSocket.close();
            } catch (IOException ignore) {
                // closing on teardown
            }
            synchronized (accepted) {
                for (Socket socket : accepted) {
                    try {
                        socket.close();
                    } catch (IOException ignore) {
                        // closing on teardown
                    }
                }
            }
        }
    }

    @Test
    public void shouldBoundTlsHandshakeByConfiguredConnectionTimeout() throws Exception {
        // given - an upstream that accepts the TCP connection but never answers the TLS handshake
        try (StalledTlsServer stalledUpstream = new StalledTlsServer()) {
            long configuredConnectionTimeoutMillis = 1000L;
            Configuration configuration = configuration().socketConnectionTimeoutInMillis(configuredConnectionTimeoutMillis);
            NettyHttpClient httpClient = new NettyHttpClient(configuration, mockServerLogger, clientEventLoopGroup, null, false);

            // when
            long startNanos = System.nanoTime();
            ExecutionException exception = assertThrows(ExecutionException.class, () ->
                httpClient
                    .sendRequest(request().withSecure(true).withHeader(HOST.toString(), "127.0.0.1:" + stalledUpstream.getPort()))
                    .get(30, TimeUnit.SECONDS));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            // then - the handshake was ended at roughly the configured bound, NOT the fixed 10,000ms default
            assertThat("handshake failed no earlier than the configured connection timeout (proves the timeout drove it, not an instant connect error), cause: " + exception.getCause(),
                elapsedMillis, greaterThanOrEqualTo(configuredConnectionTimeoutMillis - 200));
            assertThat("handshake was bounded by the configured connection timeout, not Netty's fixed 10,000ms default (elapsed " + elapsedMillis + "ms), cause: " + exception.getCause(),
                elapsedMillis, lessThan(6000L));
        }
    }

    @Test
    public void shouldSucceedWhenTlsHandshakeCompletesWithinConfiguredConnectionTimeout() throws Exception {
        // given - a real TLS upstream that completes the handshake, with a generous connection timeout
        EchoServer echoServer = new EchoServer(true);
        try {
            Configuration configuration = configuration().socketConnectionTimeoutInMillis(5000L);
            NettyHttpClient httpClient = new NettyHttpClient(configuration, mockServerLogger, clientEventLoopGroup, null, false);

            // when
            HttpResponse httpResponse = httpClient
                .sendRequest(request().withSecure(true).withHeader(HOST.toString(), "127.0.0.1:" + echoServer.getPort()))
                .get(30, TimeUnit.SECONDS);

            // then - a handshake that completes inside the bound is unaffected
            assertThat(httpResponse.getStatusCode(), is(200));
        } finally {
            stopQuietly(echoServer);
        }
    }
}
