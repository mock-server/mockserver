package org.mockserver.mock.action.http;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxyconfiguration.ForwardTargetBlockedException;
import org.mockserver.proxyconfiguration.HostLookups;
import org.slf4j.event.Level;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;

/**
 * forwardProxyAllowedHosts and forwardProxyDeniedHosts applied to a forward action and to a request MockServer sends
 * itself (a webhook), end to end to an upstream listening on localhost, with forwardProxyBlockPrivateNetworks off.
 */
public class ForwardTargetHostListsTest {

    private static final String LOOPBACK = "127.0.0.0/8,::1/128";
    private static final InetAddress PUBLIC = publicAddress();

    private final List<LogEntry> logged = new CopyOnWriteArrayList<>();
    private final MockServerLogger mockServerLogger = new MockServerLogger(ForwardTargetHostListsTest.class) {
        @Override
        public boolean isEnabledForInstance(Level level) {
            return true;
        }

        @Override
        public void logEvent(LogEntry logEntry) {
            logged.add(logEntry);
        }
    };
    private EventLoopGroup group;
    private Upstream upstream;

    private static InetAddress publicAddress() {
        try {
            return InetAddress.getByAddress(new byte[]{(byte) 203, 0, 113, 10});
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Before
    public void startUpstream() throws IOException {
        group = new NioEventLoopGroup(2);
        upstream = new Upstream(InetAddress.getByName("localhost"));
    }

    @After
    public void stopUpstream() throws IOException {
        upstream.close();
        group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }

    private static Configuration lists(String allowed, String denied) {
        return configuration()
            .forwardProxyBlockPrivateNetworks(false)
            .forwardProxyAllowedHosts(allowed)
            .forwardProxyDeniedHosts(denied)
            .socketConnectionTimeoutInMillis(500L)
            .forwardConnectionPoolEnabled(false);
    }

    private HttpForwardActionResult forwardToUpstream(Configuration configuration) {
        NettyHttpClient client = new NettyHttpClient(configuration, mockServerLogger, group, null, true, null);
        return new HttpForwardActionHandler(mockServerLogger, configuration, client)
            .handle(forward().withHost("localhost").withPort(upstream.port()), request("/lists").withHeader("Host", "localhost:" + upstream.port()));
    }

    /**
     * A forward action refused before sending is answered 502, as a forwardProxyBlockPrivateNetworks refusal is, and
     * the reason logged at WARN.
     */
    private void assertRefusedWith502(HttpForwardActionResult result, String reason) throws Exception {
        assertThat(result.getHttpResponse().get(30, TimeUnit.SECONDS).getStatusCode(), is(502));
        assertThat(logged.stream().filter(entry -> entry.getLogLevel() == Level.WARN).map(LogEntry::getMessage).collect(Collectors.toList()),
            hasItem(allOf(containsString("forward action blocked by SSRF policy"), containsString(reason))));
        assertThat(upstream.connections(), is(0));
    }

    private static ForwardTargetBlockedException refusal(CompletableFuture<HttpResponse> response) {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> response.get(30, TimeUnit.SECONDS));
        ForwardTargetBlockedException refused = ForwardTargetBlockedException.in(failure);
        assertThat(refused, notNullValue());
        return refused;
    }

    @Test
    public void shouldForwardToAnAllowedHost() throws Exception {
        HttpResponse response = forwardToUpstream(lists("localhost", "")).getHttpResponse().get(30, TimeUnit.SECONDS);

        assertThat(response.getStatusCode(), is(200));
        assertThat(upstream.connections(), is(1));
    }

    @Test
    public void shouldRefuseAHostNotOnTheAllowList() throws Exception {
        assertRefusedWith502(forwardToUpstream(lists("*.example.com", "")), "not in forwardProxyAllowedHosts: localhost");
    }

    @Test
    public void shouldRefuseADeniedHost() throws Exception {
        assertRefusedWith502(forwardToUpstream(lists("", "LOCALHOST")), "denied by forwardProxyDeniedHosts: localhost");
    }

    @Test
    public void shouldRefuseAnAllowedNameThatResolvesIntoADeniedCidr() throws Exception {
        assertRefusedWith502(forwardToUpstream(lists("localhost", LOOPBACK)), "denied by forwardProxyDeniedHosts: localhost (");
    }

    @Test
    public void shouldRefuseANameThatRebindsIntoADeniedCidrAfterTheCheck() {
        try (HostLookups lookups = HostLookups.answer("localhost", PUBLIC, InetAddress.getLoopbackAddress())) {
            ForwardTargetBlockedException refused = refusal(forwardToUpstream(lists("", LOOPBACK)).getHttpResponse());

            assertThat(refused.getMessage(), containsString("denied by forwardProxyDeniedHosts"));
            assertThat(lookups.lookups(), is(2));
        }
        assertThat(upstream.connections(), is(0));
    }

    @Test
    public void shouldRefuseAWebhookToADeniedHost() {
        HttpState httpState = mock(HttpState.class);
        when(httpState.getMockServerLogger()).thenReturn(mockServerLogger);
        when(httpState.getUniqueLoopPreventionHeaderName()).thenReturn("x-forwarded-by");
        when(httpState.getUniqueLoopPreventionHeaderValue()).thenReturn("MockServer_lists");
        HttpActionHandler actionHandler = new HttpActionHandler(lists("", "localhost"), () -> group, httpState, null, null);
        HttpRequest webhook = request("/webhook").withHeader("Host", "localhost:" + upstream.port());

        ForwardTargetBlockedException refused = refusal(actionHandler.getRequestSender().apply(webhook));

        assertThat(refused.getMessage(), containsString("denied by forwardProxyDeniedHosts"));
        assertThat(upstream.connections(), is(0));
    }

    /**
     * Counts the connections it accepts and answers each with an empty 200.
     */
    private static final class Upstream implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final AtomicInteger connections = new AtomicInteger();

        private Upstream(InetAddress address) throws IOException {
            serverSocket = new ServerSocket(0, 50, address);
            Thread acceptor = new Thread(this::accept, "host-lists-upstream");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        private void accept() {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    connections.incrementAndGet();
                    socket.setSoTimeout(5_000);
                    InputStream in = socket.getInputStream();
                    in.read(new byte[4096]);
                    OutputStream out = socket.getOutputStream();
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
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
