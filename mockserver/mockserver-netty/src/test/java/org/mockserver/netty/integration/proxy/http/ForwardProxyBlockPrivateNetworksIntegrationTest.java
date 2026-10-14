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
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.closurecallback.websocketregistry.LocalCallbackRegistry;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.mock.breakpoint.BreakpointCallbackDispatcher;
import org.mockserver.mock.breakpoint.BreakpointMatcherRegistry;
import org.mockserver.mock.breakpoint.BreakpointPhase;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.mockserver.scheduler.Scheduler;
import org.slf4j.event.Level;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.ProxyPassMapping.proxyPass;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.Retries.tryWaitForSuccess;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * forwardProxyBlockPrivateNetworks refuses a loopback destination on every route that forwards or proxies a
 * request, and allows it with the setting off. The destination here is a MockServer on 127.0.0.1.
 *
 * @author jamesdbloom
 */
public class ForwardProxyBlockPrivateNetworksIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;
    private static final String BLOCKED = "Forward to loopback address blocked";

    private static EventLoopGroup clientEventLoopGroup;
    private static ClientAndServer destination;
    // read by DestinationCallback, which the proxy instantiates by its class name
    private static volatile int destinationPort;

    private final List<LogEntry> logged = new CopyOnWriteArrayList<>();
    private ClientAndServer proxy;
    private MockServer binaryProxy;

    @BeforeClass
    public static void startDestination() {
        destination = startClientAndServer();
        destinationPort = destination.getLocalPort();
        clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(ForwardProxyBlockPrivateNetworksIntegrationTest.class.getSimpleName() + "-eventLoop"));
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
        // a clone: the event log clears the entry it is handed once it has copied it
        MockServerLogger.setGlobalLogEventListener(logEntry -> logged.add(logEntry.clone()));
    }

    @After
    public void stopProxy() {
        MockServerLogger.setGlobalLogEventListener(null);
        stopQuietly(proxy);
        stopQuietly(binaryProxy);
    }

    public static class DestinationCallback implements ExpectationForwardCallback {
        @Override
        public HttpRequest handle(HttpRequest httpRequest) {
            return request().withPath("/target").withHeader(HOST.toString(), "127.0.0.1:" + destinationPort);
        }
    }

    @Test
    public void shouldApplyTheSettingToOverrideForwardedRequest() throws Exception {
        assertRefusedOnlyWithTheSettingOn(
            block -> startClientAndServer(blockingPrivateNetworks(block)),
            server -> server
                .when(request().withPath("/override"))
                .forward(forwardOverriddenRequest(request().withPath("/target").withHeader(HOST.toString(), "127.0.0.1:" + destinationPort))),
            server -> sendTo(server, request().withPath("/override")),
            "override forwarded request action blocked by SSRF policy"
        );
    }

    @Test
    public void shouldApplyTheSettingToTheClassForwardCallback() throws Exception {
        assertRefusedOnlyWithTheSettingOn(
            block -> startClientAndServer(blockingPrivateNetworks(block)),
            server -> server
                .when(request().withPath("/classCallback"))
                .forward(callback().withCallbackClass(DestinationCallback.class)),
            server -> sendTo(server, request().withPath("/classCallback")),
            "forward class callback action blocked by SSRF policy"
        );
    }

    @Test
    public void shouldApplyTheSettingToTheObjectForwardCallback() throws Exception {
        assertRefusedOnlyWithTheSettingOn(
            block -> startClientAndServer(blockingPrivateNetworks(block)),
            server -> server
                .when(request().withPath("/objectCallback"))
                .forward(httpRequest -> request().withPath("/target").withHeader(HOST.toString(), "127.0.0.1:" + destinationPort)),
            server -> sendTo(server, request().withPath("/objectCallback")),
            "forward object callback action blocked by SSRF policy"
        );
    }

    @Test
    public void shouldApplyTheSettingToASecondaryObjectForwardCallback() throws Exception {
        String clientId = "secondary-forward-" + UUID.randomUUID();
        LocalCallbackRegistry.registerCallback(clientId, (ExpectationForwardCallback) httpRequest -> request().withPath("/target").withHeader(HOST.toString(), "127.0.0.1:" + destinationPort));
        try {
            for (boolean block : new boolean[]{true, false}) {
                logged.clear();
                proxy = startClientAndServer(blockingPrivateNetworks(block));
                proxy.upsert(new Expectation(request().withPath("/secondary"))
                    .thenRespond(response().withStatusCode(202).withPrimary(true))
                    .thenForward(new HttpObjectCallback().withClientId(clientId)));

                assertThat(sendTo(proxy, request().withPath("/secondary")).getStatusCode(), is(202));

                if (block) {
                    tryWaitForSuccess(() -> assertThat(blockedWarnings("forward object callback action blocked by SSRF policy"), not(empty())));
                    destination.verify(request().withPath("/target"), exactly(0));
                } else {
                    tryWaitForSuccess(() -> destination.verify(request().withPath("/target"), exactly(1)));
                }
                stopQuietly(proxy);
            }
        } finally {
            LocalCallbackRegistry.unregisterCallback(clientId);
        }
    }

    @Test
    public void shouldApplyTheSettingToARequestProxiedToADestinationTheClientNames() throws Exception {
        assertRefusedOnlyWithTheSettingOn(
            block -> startClientAndServer(blockingPrivateNetworks(block)),
            server -> {
            },
            server -> sendTo(server, request().withPath("/target").withHeader(HOST.toString(), "127.0.0.1:" + destinationPort)),
            "proxied request blocked by SSRF policy"
        );
    }

    @Test
    public void shouldApplyTheSettingToAProxiedRequestResumedFromABreakpoint() throws Exception {
        BreakpointMatcherRegistry.getInstance().clear();
        BreakpointCallbackDispatcher.getInstance().reset();
        try {
            assertRefusedOnlyWithTheSettingOn(
                block -> startClientAndServer(blockingPrivateNetworks(block).breakpointTimeoutMillis(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))),
                server -> server.addBreakpoint(
                    request().withPath("/viaBreakpoint"),
                    EnumSet.of(BreakpointPhase.REQUEST),
                    httpRequest -> request().withPath("/target").withHeader(HOST.toString(), "127.0.0.1:" + destinationPort),
                    null,
                    null
                ),
                server -> sendTo(server, request().withPath("/viaBreakpoint").withHeader(HOST.toString(), "127.0.0.1:" + destinationPort)),
                "proxied request blocked by SSRF policy"
            );
        } finally {
            BreakpointMatcherRegistry.getInstance().clear();
            BreakpointCallbackDispatcher.getInstance().reset();
        }
    }

    /**
     * A name for MockServer itself that it does not recognise as its own (here 127.0.0.2) is proxied, so refused;
     * listing it in noProxyHosts gets the 404 back.
     */
    @Test
    public void shouldAnswer404ToAnUnmatchedRequestForMockServerByANameOnNoProxyHosts() throws Exception {
        proxy = startClientAndServer(blockingPrivateNetworks(true));
        String ownName = "127.0.0.2:" + proxy.getLocalPort();
        assertThat(sendTo(proxy, request().withPath("/unmatched").withHeader(HOST.toString(), ownName)).getStatusCode(), is(502));
        stopQuietly(proxy);

        proxy = startClientAndServer(blockingPrivateNetworks(true).noProxyHosts("127.0.0.2"));
        assertThat(sendTo(proxy, request().withPath("/unmatched").withHeader(HOST.toString(), "127.0.0.2:" + proxy.getLocalPort())).getStatusCode(), is(404));
    }

    @Test
    public void shouldApplyTheSettingToProxyRemoteHost() throws Exception {
        assertRefusedOnlyWithTheSettingOn(
            block -> new ClientAndServer(blockingPrivateNetworks(block), "127.0.0.1", destinationPort, 0),
            server -> {
            },
            server -> sendTo(server, request().withPath("/target").withHeader(HOST.toString(), "127.0.0.1:" + server.getLocalPort())),
            "proxied request blocked by SSRF policy"
        );
    }

    @Test
    public void shouldApplyTheSettingToAProxyPassMapping() throws Exception {
        assertRefusedOnlyWithTheSettingOn(
            block -> startClientAndServer(blockingPrivateNetworks(block).proxyPassMappings(Collections.singletonList(proxyPass("/pass", "http://127.0.0.1:" + destinationPort + "/target")))),
            server -> {
            },
            server -> sendTo(server, request().withPath("/pass").withHeader(HOST.toString(), "127.0.0.1:" + server.getLocalPort())),
            "proxied request blocked by SSRF policy"
        );
    }

    @Test
    public void shouldApplyTheSettingToABinaryConnection() throws Exception {
        for (boolean block : new boolean[]{true, false}) {
            assertBinaryForward(block);
        }
    }

    private void assertBinaryForward(boolean block) throws Exception {
        String mode = "block=" + block;
        try (LineEcho upstream = new LineEcho()) {
            binaryProxy = new MockServer(blockingPrivateNetworks(block), upstream.port(), "127.0.0.1", 0);
            logged.clear();
            try (Socket client = new Socket("127.0.0.1", binaryProxy.getLocalPort())) {
                client.setSoTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                client.getOutputStream().write("hello\n".getBytes(StandardCharsets.UTF_8));
                client.getOutputStream().flush();
                if (block) {
                    assertThat(mode + ": the client's connection is closed", client.getInputStream().read(), is(-1));
                    assertThat(mode + ": nothing reached the upstream", upstream.connections(), is(0));
                    // the connection is closed only after the refusal is logged, so a second warning would be here
                    List<LogEntry> warnings = warningsSince();
                    assertThat(mode + ": the refusal is the only warning", warnings, hasSize(1));
                    assertThat(mode, String.valueOf(warnings.get(0).getMessageFormat()), containsString("which cannot be forwarded to"));
                    assertThat(mode, String.valueOf(warnings.get(0).getArguments()[2]), containsString(BLOCKED));
                } else {
                    assertThat(mode, readLine(client.getInputStream()), is("hello\n"));
                    assertThat(mode, upstream.connections(), is(1));
                }
            }
        } finally {
            stopQuietly(binaryProxy);
        }
    }

    /** At INFO, as the test log level would hide the warning. */
    private static Configuration blockingPrivateNetworks(boolean block) {
        return configuration().logLevel("INFO").forwardProxyBlockPrivateNetworks(block);
    }

    private interface Setup {
        void apply(ClientAndServer server) throws Exception;
    }

    private interface Send {
        HttpResponse send(ClientAndServer server) throws Exception;
    }

    private void assertRefusedOnlyWithTheSettingOn(Function<Boolean, ClientAndServer> start, Setup setup, Send send, String warning) throws Exception {
        proxy = start.apply(true);
        setup.apply(proxy);

        HttpResponse refused = send.send(proxy);

        assertThat(refused.getStatusCode(), is(502));
        destination.verify(request().withPath("/target"), exactly(0));
        tryWaitForSuccess(() -> assertThat(blockedWarnings(warning), not(empty())));
        stopQuietly(proxy);

        proxy = start.apply(false);
        setup.apply(proxy);

        HttpResponse allowed = send.send(proxy);

        assertThat(allowed.getStatusCode(), is(200));
        assertThat(allowed.getBodyAsString(), is("destination"));
        destination.verify(request().withPath("/target"), exactly(1));
    }

    private List<LogEntry> warningsSince() {
        return logged.stream().filter(entry -> entry.getLogLevel() == Level.WARN).collect(Collectors.toList());
    }

    /** The warnings with this message whose reason is the existing loopback refusal. */
    private List<LogEntry> blockedWarnings(String message) {
        return logged.stream()
            .filter(entry -> entry.getLogLevel() == Level.WARN && String.valueOf(entry.getMessageFormat()).contains(message))
            .filter(entry -> entry.getArguments() != null && entry.getArguments().length > 0 && String.valueOf(entry.getArguments()[0]).contains(BLOCKED))
            .collect(Collectors.toList());
    }

    private static HttpResponse sendTo(ClientAndServer server, HttpRequest request) throws Exception {
        return new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false)
            .sendRequest(request, new InetSocketAddress("127.0.0.1", server.getLocalPort()))
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int read; (read = input.read()) != -1; ) {
            line.write(read);
            if (read == '\n') {
                break;
            }
        }
        return line.toString(StandardCharsets.UTF_8.name());
    }

    /** An upstream on 127.0.0.1 that echoes each line it reads. */
    private static final class LineEcho implements AutoCloseable {
        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        private final AtomicInteger connections = new AtomicInteger();
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();

        LineEcho() throws IOException {
            Thread accept = new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket socket = serverSocket.accept();
                        connections.incrementAndGet();
                        sockets.add(socket);
                        Thread serve = new Thread(() -> {
                            try (Socket served = socket) {
                                for (String line; !(line = readLine(served.getInputStream())).isEmpty(); ) {
                                    served.getOutputStream().write(line.getBytes(StandardCharsets.UTF_8));
                                    served.getOutputStream().flush();
                                }
                            } catch (IOException closed) {
                                // the connection was closed
                            }
                        }, "line-echo-serve");
                        serve.setDaemon(true);
                        serve.start();
                    } catch (IOException closed) {
                        // close() ends the accept
                    }
                }
            }, "line-echo-accept");
            accept.setDaemon(true);
            accept.start();
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
            for (Socket socket : sockets) {
                socket.close();
            }
        }
    }
}
