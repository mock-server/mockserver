package org.mockserver.netty.integration.proxy.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.mockserver.load.LoadProfile;
import org.mockserver.load.LoadScenario;
import org.mockserver.load.LoadStep;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.LoadScenarioSerializer;
import org.slf4j.event.Level;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.AfterAction.afterAction;
import static org.mockserver.model.AfterAction.beforeAction;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.ExpectationStep.step;
import static org.mockserver.model.FailurePolicy.BEST_EFFORT;
import static org.mockserver.model.FailurePolicy.FAIL_FAST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.Retries.tryWaitForSuccess;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * forwardProxyBlockPrivateNetworks refuses a loopback destination for every request MockServer sends itself (webhooks
 * and load scenarios) and for a request in a CONNECT tunnel, also on a server with proxyRemoteHost set, and allows
 * each with the setting off. Drift alerts share the load scenarios' sender, and are covered by unit tests.
 *
 * @author jamesdbloom
 */
public class OutboundRequestsBlockPrivateNetworksIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;
    private static final String BLOCKED = "Forward to loopback address blocked";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static EventLoopGroup clientEventLoopGroup;
    private static ClientAndServer destination;
    private static int destinationPort;

    private final List<LogEntry> logged = new CopyOnWriteArrayList<>();
    private ClientAndServer server;

    @BeforeClass
    public static void startDestination() {
        destination = startClientAndServer();
        destinationPort = destination.getLocalPort();
        clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(OutboundRequestsBlockPrivateNetworksIntegrationTest.class.getSimpleName() + "-eventLoop"));
    }

    @AfterClass
    public static void stopDestination() {
        stopQuietly(destination);
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @Before
    public void listen() {
        // a clone: the event log clears the entry it is handed once it has copied it
        MockServerLogger.setGlobalLogEventListener(logEntry -> logged.add(logEntry.clone()));
    }

    @After
    public void stopServer() {
        MockServerLogger.setGlobalLogEventListener(null);
        stopQuietly(server);
    }

    @Test
    public void shouldApplyTheSettingToAnAfterActionWebhook() throws Exception {
        for (boolean block : new boolean[]{true, false}) {
            startServer(block, new Expectation(request().withPath("/webhooked"))
                .thenRespond(response().withBody("mocked"))
                .withAfterActions(afterAction().withHttpRequest(target())));

            HttpResponse response = sendTo(server, request().withPath("/webhooked"));

            assertThat(response.getBodyAsString(), is("mocked"));
            assertRefusedOnlyWhenBlocking(block, "after-action webhook blocked by SSRF policy");
        }
    }

    @Test
    public void shouldApplyTheSettingToTheRenderedTargetOfATemplatedAfterActionWebhook() throws Exception {
        for (boolean block : new boolean[]{true, false}) {
            startServer(block, new Expectation(request().withPath("/webhooked"))
                .thenRespond(response().withBody("mocked"))
                .withAfterActions(afterAction().withHttpRequestTemplate(templatedTarget())));

            HttpResponse response = sendTo(server, request().withPath("/webhooked"));

            assertThat(response.getBodyAsString(), is("mocked"));
            assertRefusedOnlyWhenBlocking(block, "after-action webhook blocked by SSRF policy");
        }
    }

    @Test
    public void shouldApplyTheSettingToTheRenderedTargetOfATemplatedFailFastBlockingBeforeActionWebhook() throws Exception {
        for (boolean block : new boolean[]{true, false}) {
            startServer(block, new Expectation(request().withPath("/webhooked"))
                .thenRespond(response().withBody("mocked"))
                .withBeforeActions(beforeAction().withHttpRequestTemplate(templatedTarget()).withFailurePolicy(FAIL_FAST)));

            HttpResponse response = sendTo(server, request().withPath("/webhooked"));

            if (block) {
                assertThat(response.getStatusCode(), is(502));
                assertThat(response.getBodyAsString(), startsWith("before-action failed: " + BLOCKED));
            } else {
                assertThat(response.getBodyAsString(), is("mocked"));
            }
            assertRefusedOnlyWhenBlocking(block, "before-action webhook blocked by SSRF policy");
        }
    }

    @Test
    public void shouldApplyTheSettingToAFailFastBlockingBeforeActionWebhook() throws Exception {
        for (boolean block : new boolean[]{true, false}) {
            startServer(block, new Expectation(request().withPath("/webhooked"))
                .thenRespond(response().withBody("mocked"))
                .withBeforeActions(beforeAction().withHttpRequest(target()).withFailurePolicy(FAIL_FAST)));

            HttpResponse response = sendTo(server, request().withPath("/webhooked"));

            if (block) {
                assertThat(response.getStatusCode(), is(502));
                assertThat(response.getBodyAsString(), startsWith("before-action failed: " + BLOCKED));
            } else {
                assertThat(response.getBodyAsString(), is("mocked"));
            }
            assertRefusedOnlyWhenBlocking(block, "before-action webhook blocked by SSRF policy");
        }
    }

    @Test
    public void shouldApplyTheSettingToABestEffortBlockingBeforeActionWebhook() throws Exception {
        for (boolean block : new boolean[]{true, false}) {
            startServer(block, new Expectation(request().withPath("/webhooked"))
                .thenRespond(response().withBody("mocked"))
                .withBeforeActions(beforeAction().withHttpRequest(target()).withFailurePolicy(BEST_EFFORT)));

            HttpResponse response = sendTo(server, request().withPath("/webhooked"));

            assertThat(response.getBodyAsString(), is("mocked"));
            assertRefusedOnlyWhenBlocking(block, "before-action webhook blocked by SSRF policy");
        }
    }

    @Test
    public void shouldApplyTheSettingToAFailFastBlockingStepWebhook() throws Exception {
        for (boolean block : new boolean[]{true, false}) {
            startServer(block, new Expectation(request().withPath("/webhooked"))
                .withSteps(
                    step().withHttpRequest(target()).withFailurePolicy(FAIL_FAST),
                    step().withHttpResponse(response().withBody("mocked")).withResponder(true)
                ));

            HttpResponse response = sendTo(server, request().withPath("/webhooked"));

            if (block) {
                assertThat(response.getStatusCode(), is(502));
                assertThat(response.getBodyAsString(), startsWith("step failed: " + BLOCKED));
            } else {
                assertThat(response.getBodyAsString(), is("mocked"));
            }
            assertRefusedOnlyWhenBlocking(block, "step webhook blocked by SSRF policy");
        }
    }

    @Test
    public void shouldApplyTheSettingToAStepWebhookAfterTheResponder() throws Exception {
        for (boolean block : new boolean[]{true, false}) {
            startServer(block, new Expectation(request().withPath("/webhooked"))
                .withSteps(
                    step().withHttpResponse(response().withBody("mocked")).withResponder(true),
                    step().withHttpRequest(target())
                ));

            HttpResponse response = sendTo(server, request().withPath("/webhooked"));

            assertThat(response.getBodyAsString(), is("mocked"));
            assertRefusedOnlyWhenBlocking(block, "step webhook blocked by SSRF policy");
        }
    }

    @Test
    public void shouldApplyTheSettingToALoadScenario() throws Exception {
        for (boolean block : new boolean[]{true, false}) {
            resetDestination();
            logged.clear();
            server = startClientAndServer(blockingPrivateNetworks(block).loadGenerationEnabled(true));
            LoadScenario scenario = new LoadScenario()
                .withName("private-network-load")
                .withProfile(LoadProfile.constant(1, 10_000L))
                .withSteps(new LoadStep().withRequest(target()).withThinkTime(Delay.milliseconds(20)));
            try {
                assertThat(sendTo(server, request().withMethod("PUT").withPath("/mockserver/loadScenario").withBody(new LoadScenarioSerializer(new MockServerLogger()).serialize(scenario))).getStatusCode(), is(200));
                assertThat(sendTo(server, request().withMethod("PUT").withPath("/mockserver/loadScenario/start").withBody("{ \"name\": \"private-network-load\" }")).getStatusCode(), is(200));

                if (block) {
                    tryWaitForSuccess(() -> assertThat(loadStatus().get("failed").asLong(), greaterThanOrEqualTo(3L)));
                    assertThat(loadStatus().get("succeeded").asLong(), is(0L));
                    destination.verify(request().withPath("/target"), exactly(0));
                    // each refused request is counted, and the refusal is logged once for the run
                    assertThat(blockedWarnings("request blocked by SSRF policy"), hasSize(1));
                } else {
                    tryWaitForSuccess(() -> destination.verify(request().withPath("/target"), atLeast(3)));
                }
            } finally {
                sendTo(server, request().withMethod("PUT").withPath("/mockserver/loadScenario/stop").withBody("{ \"all\": true }"));
                sendTo(server, request().withMethod("DELETE").withPath("/mockserver/loadScenario"));
                stopQuietly(server);
            }
        }
    }

    // the tunnel goes to MockServer itself, never to proxyRemoteHost, and the request in it is proxied to the CONNECT target
    @Test
    public void shouldApplyTheSettingToARequestInATunnelOnAServerWithProxyRemoteHost() throws Exception {
        for (boolean block : new boolean[]{true, false}) {
            try (CountingUpstream upstream = new CountingUpstream()) {
                resetDestination();
                logged.clear();
                server = new ClientAndServer(blockingPrivateNetworks(block), "127.0.0.1", upstream.port(), 0);
                try (Socket client = new Socket("127.0.0.1", server.getLocalPort())) {
                    client.setSoTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                    String target = "127.0.0.1:" + destinationPort;
                    write(client, "CONNECT " + target + " HTTP/1.1\r\nHost: " + target + "\r\n\r\n");
                    assertThat(String.valueOf(logMessages()), readHead(client.getInputStream()), startsWith("HTTP/1.1 200"));

                    write(client, "GET /target HTTP/1.1\r\nHost: " + target + "\r\nContent-Length: 0\r\n\r\n");

                    assertTunnelledRequestRefusedOnlyWhenBlocking(block, readHead(client.getInputStream()));
                    assertThat(upstream.connections(), is(0));
                }
                stopQuietly(server);
            }
        }
    }

    private void assertTunnelledRequestRefusedOnlyWhenBlocking(boolean block, String responseHead) {
        if (block) {
            assertThat(responseHead, startsWith("HTTP/1.1 502"));
            destination.verify(request().withPath("/target"), exactly(0));
            tryWaitForSuccess(() -> assertThat(blockedWarnings("proxied request blocked by SSRF policy"), hasSize(1)));
        } else {
            assertThat(responseHead, startsWith("HTTP/1.1 200"));
            destination.verify(request().withPath("/target"), exactly(1));
            assertThat(blockedWarnings("proxied request blocked by SSRF policy"), hasSize(0));
        }
    }

    private void startServer(boolean block, Expectation expectation) {
        stopQuietly(server);
        resetDestination();
        logged.clear();
        server = startClientAndServer(blockingPrivateNetworks(block));
        server.upsert(expectation);
    }

    private void assertRefusedOnlyWhenBlocking(boolean block, String warning) {
        if (block) {
            tryWaitForSuccess(() -> assertThat(blockedWarnings(warning), hasSize(1)));
            destination.verify(request().withPath("/target"), exactly(0));
            // the refusal is the only entry about the webhook
            assertThat(webhookEntries(), hasSize(1));
        } else {
            tryWaitForSuccess(() -> destination.verify(request().withPath("/target"), exactly(1)));
            assertThat(blockedWarnings(warning), hasSize(0));
        }
    }

    private static void resetDestination() {
        destination.reset();
        destination
            .when(request().withPath("/target"))
            .respond(response().withStatusCode(200).withBody("destination"));
    }

    private static HttpRequest target() {
        return request().withPath("/target").withHeader(HOST.toString(), "127.0.0.1:" + destinationPort);
    }

    /** {@link #target()}, with its loopback Host header rendered by the template. */
    private static HttpTemplate templatedTarget() {
        return template(HttpTemplate.TemplateType.VELOCITY, "{ \"path\": \"/target\", \"headers\": [ { \"name\": \"Host\", \"values\": [ \"127.0.0.#set($octet = 1)$octet:" + destinationPort + "\" ] } ] }");
    }

    private JsonNode loadStatus() throws Exception {
        return OBJECT_MAPPER.readTree(sendTo(server, request().withMethod("GET").withPath("/mockserver/loadScenario/private-network-load")).getBodyAsString());
    }

    /** At INFO, as the test log level would hide the warning. */
    private static Configuration blockingPrivateNetworks(boolean block) {
        return configuration().logLevel("INFO").forwardProxyBlockPrivateNetworks(block);
    }

    /** The warnings with this message whose reason is the existing loopback refusal. */
    private List<LogEntry> blockedWarnings(String message) {
        return logged.stream()
            .filter(entry -> entry.getLogLevel() == Level.WARN && String.valueOf(entry.getMessageFormat()).contains(message))
            .filter(entry -> entry.getArguments() != null && Arrays.stream(entry.getArguments()).anyMatch(argument -> String.valueOf(argument).contains(BLOCKED)))
            .collect(Collectors.toList());
    }

    private List<String> logMessages() {
        return logged.stream().map(LogEntry::getMessage).collect(Collectors.toList());
    }

    private List<LogEntry> webhookEntries() {
        return logged.stream()
            .filter(entry -> String.valueOf(entry.getMessageFormat()).contains("webhook"))
            .collect(Collectors.toList());
    }

    private static HttpResponse sendTo(ClientAndServer server, HttpRequest request) throws Exception {
        return new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false)
            .sendRequest(request, new InetSocketAddress("127.0.0.1", server.getLocalPort()))
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void write(Socket socket, String text) throws IOException {
        socket.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }

    /** Reads a response's status line and headers, leaving any body unread. */
    private static String readHead(InputStream input) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        while (!head.toString(StandardCharsets.UTF_8.name()).endsWith("\r\n\r\n")) {
            int read = input.read();
            if (read == -1) {
                break;
            }
            head.write(read);
        }
        return head.toString(StandardCharsets.UTF_8.name());
    }

    /** An upstream on 127.0.0.1 that counts its connections. */
    private static final class CountingUpstream implements AutoCloseable {
        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        private final AtomicInteger connections = new AtomicInteger();
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();

        CountingUpstream() throws IOException {
            Thread accept = new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        sockets.add(serverSocket.accept());
                        connections.incrementAndGet();
                    } catch (IOException closed) {
                        // close() ends the accept
                    }
                }
            }, "counting-upstream-accept");
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
