package org.mockserver.netty.integration.proxy.http;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.mockserver.scheduler.Scheduler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * forwardProxyAllowedHosts and forwardProxyDeniedHosts, with forwardProxyBlockPrivateNetworks off, on the unmatched
 * proxy route and a binary connection of a running server. The destination is on 127.0.0.1.
 */
public class ForwardProxyHostListsIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;
    private static final String DENY_LOOPBACK = "127.0.0.0/8,::1/128";

    private static EventLoopGroup clientEventLoopGroup;
    private static ClientAndServer destination;

    private ClientAndServer proxy;
    private MockServer binaryProxy;

    @BeforeClass
    public static void startDestination() {
        destination = startClientAndServer();
        clientEventLoopGroup = new NioEventLoopGroup(2, new Scheduler.SchedulerThreadFactory(ForwardProxyHostListsIntegrationTest.class.getSimpleName() + "-eventLoop"));
    }

    @AfterClass
    public static void stopDestination() {
        stopQuietly(destination);
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Before
    public void resetDestination() {
        destination.reset();
        destination
            .when(request().withPath("/target"))
            .respond(response().withStatusCode(200).withBody("destination"));
    }

    @After
    public void stopProxy() {
        stopQuietly(proxy);
        stopQuietly(binaryProxy);
    }

    private static Configuration lists(String allowed, String denied) {
        return configuration().forwardProxyBlockPrivateNetworks(false).forwardProxyAllowedHosts(allowed).forwardProxyDeniedHosts(denied);
    }

    private HttpResponse proxyToDestination(Configuration configuration) throws Exception {
        proxy = startClientAndServer(configuration);
        HttpRequest proxied = request().withPath("/target").withHeader(HOST.toString(), "127.0.0.1:" + destination.getLocalPort());
        return new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false)
            .sendRequest(proxied, new InetSocketAddress("127.0.0.1", proxy.getLocalPort()))
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void shouldRefuseAProxiedRequestToADeniedAddress() throws Exception {
        assertThat(proxyToDestination(lists("", DENY_LOOPBACK)).getStatusCode(), is(502));
        destination.verify(request().withPath("/target"), exactly(0));
    }

    @Test
    public void shouldRefuseAProxiedRequestToAnAddressNotOnTheAllowList() throws Exception {
        assertThat(proxyToDestination(lists("*.example.com", "")).getStatusCode(), is(502));
        destination.verify(request().withPath("/target"), exactly(0));
    }

    @Test
    public void shouldProxyARequestToAnAllowedAddress() throws Exception {
        HttpResponse response = proxyToDestination(lists("127.0.0.1", ""));

        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBodyAsString(), is("destination"));
        destination.verify(request().withPath("/target"), exactly(1));
    }

    @Test
    public void shouldCloseABinaryConnectionToADeniedAddress() throws Exception {
        try (Upstream upstream = new Upstream()) {
            binaryProxy = new MockServer(lists("", DENY_LOOPBACK), upstream.port(), "127.0.0.1", 0);
            try (Socket client = new Socket("127.0.0.1", binaryProxy.getLocalPort())) {
                client.setSoTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                client.getOutputStream().write("hello\n".getBytes(StandardCharsets.UTF_8));
                client.getOutputStream().flush();

                assertThat(client.getInputStream().read(), is(-1));
            }
            assertThat(upstream.connections(), is(0));
        }
    }

    /**
     * Counts the connections it accepts and echoes what each sends.
     */
    private static final class Upstream implements AutoCloseable {
        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        private final AtomicInteger connections = new AtomicInteger();

        private Upstream() throws IOException {
            Thread acceptor = new Thread(this::accept, "host-lists-binary-upstream");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        private void accept() {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    connections.incrementAndGet();
                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();
                    byte[] buffer = new byte[256];
                    int read = in.read(buffer);
                    if (read > 0) {
                        out.write(buffer, 0, read);
                        out.flush();
                    }
                } catch (IOException ignored) {
                    // closed, or a client went away
                }
            }
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        int connections() {
            return connections.get();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
