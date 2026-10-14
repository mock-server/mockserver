package org.mockserver.netty.integration.proxy.http;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.model.HttpRequest;
import org.mockserver.netty.MockServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.ProxyPassMapping.proxyPass;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A {@code proxyPassMappings} entry, and a WebSocket upgrade a forward relays, whose target leads back to the same
 * MockServer stop after {@code forwardLoopMaxHops} hops with a {@code 508}, as a forwarded request does. Every loop
 * here is real: each hop is a new connection back to a running server. A WebSocket upgrade proxied because no
 * expectation matched is not counted, as an unmatched HTTP request proxied onward is not.
 */
public class ProxyPassAndWebSocketLoopIntegrationTest {

    private static final String HEADER = "x-mockserver-forwarded-by";
    private static final String REFUSED = "returning 508 Loop Detected in place of forwarding request";
    private static final String PROXY_PASS_LOOP = "MockServer did not forward this request because it has already forwarded it 10 times, so the proxyPassMappings entry's target leads back to this MockServer, where the request matched the mapping again: check the mapping's target URI, or raise forwardLoopMaxHops if the repeated forwarding is intended";
    private static final String FORWARD_LOOP = "MockServer did not forward this request because it has already forwarded it 10 times, so the forward's target leads back to this MockServer, where the request matched a forward again: check the forward's host and port, or raise forwardLoopMaxHops if the repeated forwarding is intended";

    private static Configuration firstConfiguration;
    private static Configuration secondConfiguration;
    private static MockServer first;
    private static MockServer second;
    private static MockServerClient firstClient;
    private static MockServerClient secondClient;
    private static MockServer detectionOff;
    private static MockServerClient detectionOffClient;

    @BeforeClass
    public static void startServers() {
        // the default forwardLoopMaxHops; WARN so the refusal is logged whatever mockserver.testLogLevel is
        firstConfiguration = configuration().logLevel("WARN").attemptToProxyIfNoMatchingExpectation(true);
        secondConfiguration = configuration().logLevel("WARN");
        Configuration detectionOffConfiguration = configuration().logLevel("WARN").forwardLoopMaxHops(0);
        first = new MockServer(firstConfiguration, 0);
        second = new MockServer(secondConfiguration, 0);
        detectionOff = new MockServer(detectionOffConfiguration, 0);
        String firstAddress = "http://127.0.0.1:" + first.getLocalPort();
        String secondAddress = "http://127.0.0.1:" + second.getLocalPort();
        // the mappings name the servers' own ports, which are known only once they have started
        firstConfiguration.proxyPassMappings(Arrays.asList(
            proxyPass("/pp-loop", firstAddress + "/pp-loop"),
            proxyPass("/pp-rewrite", firstAddress + "/pp-target"),
            proxyPass("/pp-ping-pong", secondAddress + "/pp-ping-pong")
        ));
        secondConfiguration.proxyPassMappings(Arrays.asList(
            proxyPass("/pp-ping-pong", firstAddress + "/pp-ping-pong")
        ));
        detectionOffConfiguration.proxyPassMappings(Arrays.asList(
            proxyPass("/pp-off", secondAddress + "/pp-off")
        ));
        firstClient = new MockServerClient("127.0.0.1", first.getLocalPort());
        secondClient = new MockServerClient("127.0.0.1", second.getLocalPort());
        detectionOffClient = new MockServerClient("127.0.0.1", detectionOff.getLocalPort());
    }

    @AfterClass
    public static void stopServers() {
        stopQuietly(firstClient);
        stopQuietly(secondClient);
        stopQuietly(detectionOffClient);
        stopQuietly(first);
        stopQuietly(second);
        stopQuietly(detectionOff);
    }

    @Before
    public void reset() {
        firstClient.reset();
        secondClient.reset();
        detectionOffClient.reset();
    }

    @Test
    public void shouldAnswerAProxyPassMappingBackToItselfWith508AfterTenHops() throws Exception {
        Response response = send(first, "/pp-loop");

        assertThat(response.toString(), response.status, is(508));
        assertThat(response.body, is(PROXY_PASS_LOOP));
        HttpRequest[] arrivals = firstClient.retrieveRecordedRequests(request().withPath("/pp-loop"));
        assertThat("the client's request and ten proxied hops", arrivals, arrayWithSize(11));
        assertThat(arrivals[10].getHeader(HEADER), hasSize(10));
        assertThat(firstClient.retrieveLogMessages(request().withPath("/pp-loop")), containsString(REFUSED));
    }

    @Test
    public void shouldProxyPassBackToItselfToAPathAnExpectationAnswers() throws Exception {
        firstClient.when(request().withPath("/pp-target")).respond(response().withBody("answered by an expectation"));

        Response response = send(first, "/pp-rewrite");

        assertThat(response.toString(), response.status, is(200));
        assertThat(response.body, is("answered by an expectation"));
        assertThat(firstClient.retrieveRecordedRequests(request().withPath("/pp-target"))[0].getHeader(HEADER), contains(containsString("MockServer_")));
    }

    @Test
    public void shouldStopAProxyPassLoopBetweenTwoInstances() throws Exception {
        Response response = send(first, "/pp-ping-pong");

        assertThat(response.toString(), response.status, is(508));
        assertThat(response.body, is(PROXY_PASS_LOOP));
        assertThat("each instance counts only its own entries", firstClient.retrieveRecordedRequests(request().withPath("/pp-ping-pong")), arrayWithSize(11));
        assertThat(secondClient.retrieveRecordedRequests(request().withPath("/pp-ping-pong")), arrayWithSize(10));
    }

    @Test
    public void shouldAnswerAWebSocketUpgradeForwardedBackToItselfWith508AfterTenHops() throws Exception {
        firstClient.when(request().withPath("/ws-loop")).forward(forward().withHost("127.0.0.1").withPort(first.getLocalPort()));

        Response response = upgrade(first, "/ws-loop");

        assertThat(response.toString(), response.status, is(508));
        assertThat("each outer hop relays the innermost hop's 508", response.body, is(FORWARD_LOOP));
        HttpRequest[] arrivals = firstClient.retrieveRecordedRequests(request().withPath("/ws-loop"));
        assertThat("the client's upgrade and ten relayed upgrades", arrivals, arrayWithSize(11));
        assertThat(arrivals[10].getHeader(HEADER), hasSize(10));
        String log = firstClient.retrieveLogMessages(request().withPath("/ws-loop"));
        assertThat(log, containsString(REFUSED));
        assertThat("an outer hop logs the 508 it relays", log, containsString("upstream answered the WebSocket handshake with 508 Loop Detected"));
    }

    @Test
    public void shouldSendTheCountOnAWebSocketUpgradeRelayedToAnotherInstance() throws Exception {
        firstClient.when(request().withPath("/ws-other")).forward(forward().withHost("127.0.0.1").withPort(second.getLocalPort()));

        upgrade(first, "/ws-other");

        List<String> atSecond = secondClient.retrieveRecordedRequests(request().withPath("/ws-other"))[0].getHeader(HEADER);
        assertThat(atSecond, contains(containsString("MockServer_")));
    }

    @Test
    public void shouldNeitherCountNorMarkAWebSocketUpgradeProxiedBecauseNoExpectationMatched() throws Exception {
        String proxiedUpgrade = "GET /ws-proxied HTTP/1.1\r\nHost: 127.0.0.1:" + second.getLocalPort() + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n";

        exchange(first, proxiedUpgrade);

        HttpRequest[] atSecond = secondClient.retrieveRecordedRequests(request().withPath("/ws-proxied"));
        assertThat("the upgrade was relayed to the upstream", atSecond, arrayWithSize(1));
        assertThat(atSecond[0].getHeader(HEADER), is(empty()));
    }

    @Test
    public void shouldNotSendTheHeaderOnEitherRouteWhenLoopDetectionIsOff() throws Exception {
        detectionOffClient.when(request().withPath("/ws-off")).forward(forward().withHost("127.0.0.1").withPort(second.getLocalPort()));
        secondClient.when(request().withPath("/pp-off")).respond(response().withBody("answered"));

        Response proxyPassed = send(detectionOff, "/pp-off");
        upgrade(detectionOff, "/ws-off");

        assertThat(proxyPassed.toString(), proxyPassed.status, is(200));
        assertThat(secondClient.retrieveRecordedRequests(request().withPath("/pp-off"))[0].getHeader(HEADER), is(empty()));
        HttpRequest[] upgradesAtSecond = secondClient.retrieveRecordedRequests(request().withPath("/ws-off"));
        assertThat(upgradesAtSecond, arrayWithSize(1));
        assertThat(upgradesAtSecond[0].getHeader(HEADER), is(empty()));
    }

    private static Response send(MockServer mockServer, String path) throws IOException {
        return exchange(mockServer, "GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + mockServer.getLocalPort() + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
    }

    private static Response upgrade(MockServer mockServer, String path) throws IOException {
        return exchange(mockServer, "GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + mockServer.getLocalPort() + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n");
    }

    private static Response exchange(MockServer mockServer, String head) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            socket.setSoTimeout(30_000);
            OutputStream output = socket.getOutputStream();
            output.write(head.getBytes(StandardCharsets.ISO_8859_1));
            output.flush();
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            InputStream input = socket.getInputStream();
            byte[] buffer = new byte[16 * 1024];
            for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
                received.write(buffer, 0, read);
            }
            return new Response(received.toString(StandardCharsets.ISO_8859_1.name()));
        }
    }

    private static final class Response {
        private final int status;
        private final String body;
        private final String raw;

        private Response(String raw) {
            this.raw = raw;
            this.status = raw.length() > 12 ? Integer.parseInt(raw.substring(9, 12)) : -1;
            int bodyStart = raw.indexOf("\r\n\r\n");
            this.body = bodyStart < 0 || bodyStart + 4 == raw.length() ? null : raw.substring(bodyStart + 4);
        }

        @Override
        public String toString() {
            return raw;
        }
    }
}
