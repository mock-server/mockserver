package org.mockserver.netty.integration.mock;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Headers;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;
import org.mockserver.netty.MockServerCaTrustTestSupport;
import org.mockserver.netty.integration.Http2TestClient;
import org.mockserver.netty.integration.NettyBufferLeaks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.Delay.delay;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * An expectation whose response has a {@code 1xx} status and nothing after it, over HTTP/2: the {@code 1xx} is sent
 * as an interim response, which RFC 9113 section 8.1 forbids to end a stream, and the stream is then reset with
 * {@code NO_ERROR}. That is the same on a connection made straight to MockServer and through a CONNECT or SOCKS
 * tunnel, so the connection has no stream left open and is closed as idle. An interim response that a final one
 * follows is left alone.
 */
public class Http2FinalInformationalResponseIntegrationTest {

    private static final long IDLE_MILLIS = 1000;
    private static final String TARGET_HOST = "localhost";
    private static final int TARGET_PORT = 443;

    private static MockServer mockServer;
    private static MockServerClient mockServerClient;
    private static EventLoopGroup clientGroup;
    private static int leaksBefore;

    public enum Route {
        H2C(false, false), TLS(false, true), CONNECT_TLS(true, true), SOCKS5_H2C(true, false);

        private final boolean tunnel;
        private final boolean tls;

        Route(boolean tunnel, boolean tls) {
            this.tunnel = tunnel;
            this.tls = tls;
        }
    }

    @BeforeClass
    public static void startServer() {
        leaksBefore = NettyBufferLeaks.recorded();
        mockServer = new MockServer(configuration().logLevel("WARN").inboundConnectionIdleTimeoutMillis(IDLE_MILLIS), 0);
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());
        clientGroup = new NioEventLoopGroup(2);
    }

    @AfterClass
    public static void stopServer() throws Exception {
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
        if (clientGroup != null) {
            clientGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly(10, TimeUnit.SECONDS);
        }
        NettyBufferLeaks.assertNoneSince(leaksBefore);
    }

    @Before
    public void resetServer() {
        mockServerClient.reset();
        mockServerClient.when(request().withPath("/processing")).respond(response().withStatusCode(102));
        mockServerClient.when(request().withPath("/hints")).respond(response().withStatusCode(103).withHeader("link", "</style.css>; rel=preload"));
        mockServerClient.when(request().withPath("/served")).respond(response().withBody("served"));
    }

    @Test
    public void shouldSendAMockedInformationalResponseAsAnInterimResponseAndThenResetItsStream() throws Exception {
        for (Route route : Route.values()) {
            for (String path : Arrays.asList("/processing", "/hints")) {
                String where = route + " " + path;
                try (Http2TestClient client = connect(route)) {
                    Http2TestClient.Exchange informational = client.send(headers(route, HttpMethod.GET, path), true);

                    assertThat(where, informational.interimStatus(), is(path.equals("/hints") ? 103 : 102));
                    assertThat(where + ": the 1xx does not end the stream itself", informational.interimEndedStream(), is(false));
                    assertThat(where, informational.resetErrorCode(), is(Http2Error.NO_ERROR.code()));
                    assertThat(where + ": no final response", informational.isComplete(), is(false));

                    assertThat(where + ": the connection carries on", client.send(headers(route, HttpMethod.GET, "/served"), true).body(), is("served"));
                    assertThat(where, client.isOpen(), is(true));
                }
            }
        }
        assertThat("ending a stream this way is not a fault", warningsAndErrors(), is(empty()));
        assertThat("one for each expectation, on whichever route it was first served", finalInformationalWarningStatuses(), containsInAnyOrder(102, 103));
    }

    @Test
    public void shouldWarnOnceForEachExpectationNotForEachRequest() throws Exception {
        for (int request = 0; request < 3; request++) {
            try (Http2TestClient client = connect(Route.H2C)) {
                assertThat(client.send(headers(Route.H2C, HttpMethod.GET, "/processing"), true).resetErrorCode(), is(Http2Error.NO_ERROR.code()));
            }
        }
        assertThat(finalInformationalWarningStatuses(), contains(102));

        String warning = finalInformationalWarnings().get(0);
        assertThat("names the expectation", warning, containsString(mockServerClient.retrieveActiveExpectations(request().withPath("/processing"))[0].getId()));
        assertThat("says what happens", warning, containsString("reset the stream with NO_ERROR"));
        assertThat("and what to do", warning, containsString("add a protocol of HTTP_1_1"));

        mockServerClient.when(request().withPath("/processing-again")).respond(response().withStatusCode(102));
        try (Http2TestClient client = connect(Route.TLS)) {
            client.send(headers(Route.TLS, HttpMethod.GET, "/processing-again"), true).resetErrorCode();
            client.send(headers(Route.TLS, HttpMethod.GET, "/processing"), true).resetErrorCode();
        }
        assertThat("another expectation is warned about once too", finalInformationalWarningStatuses(), contains(102, 102));
        assertThat(warningsAndErrors(), is(empty()));
    }

    @Test
    public void shouldNotWarnAboutAFinalInformationalResponseOverHttp11() throws Exception {
        try (Socket socket = new Socket("localhost", mockServer.getLocalPort())) {
            socket.setSoTimeout(10000);
            socket.getOutputStream().write("GET /processing HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            String statusLine = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            assertThat(statusLine, startsWith("HTTP/1.1 102"));
        }
        assertThat(finalInformationalWarnings(), is(empty()));
        assertThat(warningsAndErrors(), is(empty()));
    }

    /**
     * A delayed response is written from another thread, one with {@code closeSocket} has its stream closed as soon
     * as it is written, which would reset a stream not yet reset with {@code CANCEL}, and one with a chunk size and
     * delay would be written in pieces.
     */
    @Test
    public void shouldResetTheStreamWithNoErrorHoweverTheResponseIsWritten() throws Exception {
        mockServerClient.when(request().withPath("/delayed")).respond(response().withStatusCode(102).withDelay(TimeUnit.MILLISECONDS, 100));
        mockServerClient.when(request().withPath("/closed")).respond(response().withStatusCode(102).withConnectionOptions(connectionOptions().withCloseSocket(true)));
        mockServerClient.when(request().withPath("/delayed-and-closed")).respond(response().withStatusCode(102).withDelay(TimeUnit.MILLISECONDS, 100).withConnectionOptions(connectionOptions().withCloseSocket(true)));
        // a response with a chunk size and delay is written in pieces, which a 1xx cannot be over HTTP/2
        mockServerClient.when(request().withPath("/chunked")).respond(response().withStatusCode(102).withConnectionOptions(connectionOptions().withChunkSize(2).withChunkDelay(delay(TimeUnit.MILLISECONDS, 10))));
        mockServerClient.when(request().withPath("/chunked-with-a-body")).respond(response().withStatusCode(102).withBody("a body no 1xx carries").withConnectionOptions(connectionOptions().withChunkSize(2).withChunkDelay(delay(TimeUnit.MILLISECONDS, 10))));
        mockServerClient.when(request().withPath("/chunk-size-only")).respond(response().withStatusCode(102).withConnectionOptions(connectionOptions().withChunkSize(2)));
        for (Route route : Route.values()) {
            for (String path : Arrays.asList("/delayed", "/closed", "/delayed-and-closed", "/chunked", "/chunked-with-a-body", "/chunk-size-only")) {
                String where = route + " " + path;
                try (Http2TestClient client = connect(route)) {
                    Http2TestClient.Exchange informational = client.send(headers(route, HttpMethod.GET, path), true);

                    assertThat(where, informational.interimStatus(), is(102));
                    assertThat(where, informational.interimEndedStream(), is(false));
                    assertThat(where, informational.resetErrorCode(), is(Http2Error.NO_ERROR.code()));
                    assertThat(where + ": only the stream is closed", client.send(headers(route, HttpMethod.GET, "/served"), true).body(), is("served"));
                }
            }
        }
        assertThat(warningsAndErrors(), is(empty()));
        assertThat("one for each of the six expectations", finalInformationalWarningStatuses(), contains(102, 102, 102, 102, 102, 102));
    }

    @Test
    public void shouldCloseAConnectionAsIdleAfterItsMockedInformationalResponse() throws Exception {
        for (Route route : Route.values()) {
            try (Http2TestClient client = connect(route)) {
                long sent = System.nanoTime();
                assertThat(route.name(), client.send(headers(route, HttpMethod.GET, "/processing"), true).interimStatus(), is(102));

                assertThat(route + ": closed as idle", client.closedWithin(10), is(true));
                assertThat(route + ": and not before the idle timeout", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sent), greaterThanOrEqualTo(IDLE_MILLIS - 50));
            }
            assertThat(route + ": both legs of a tunnel are closed", openConnectionsBecomeNone(), is(true));
        }
    }

    /**
     * The {@code 100} here is a real interim response: MockServer's own answer to {@code Expect: 100-continue} on a
     * direct connection, the relay's in a tunnel. MockServer has no way to mock a {@code 103} that a final response
     * follows.
     */
    @Test
    public void shouldLeaveAnInterimResponseThatAFinalResponseFollowsAlone() throws Exception {
        for (Route route : Route.values()) {
            try (Http2TestClient client = connect(route)) {
                Http2TestClient.Exchange upload = client.send(headers(route, HttpMethod.POST, "/served").add("expect", "100-continue"), false);
                assertThat(route.name(), upload.interimStatus(), is(100));
                assertThat(route.name(), upload.interimEndedStream(), is(false));

                upload.data("request body", true);

                assertThat(route.name(), upload.status(), is(200));
                assertThat(route.name(), upload.body(), is("served"));
                // on the same connection, so a reset of the first stream would have arrived by the time this is answered
                assertThat(route.name(), client.send(headers(route, HttpMethod.GET, "/served"), true).body(), is("served"));
                assertThat(route + ": not reset", upload.isReset(), is(false));
            }
        }
        assertThat(uploads(), contains("request body", "", "request body", "", "request body", "", "request body", ""));
        assertThat(warningsAndErrors(), is(empty()));
    }

    /**
     * The JDK's client does not take a {@code 1xx} for the response, so before the stream was reset it waited for a
     * response that never came. It does not speak cleartext HTTP/2 without an upgrade, so only the TLS routes.
     */
    @Test
    public void shouldFailAJdkClientsRequestAtOnceInsteadOfLeavingItWaiting() throws Exception {
        // before 17.0.17 (JDK-8307648) the JDK client queues an RST_STREAM that follows a 1xx and never acts on it
        Runtime.Version jdk = Runtime.version();
        assumeTrue("JDK " + jdk + " ignores a reset after a 1xx", jdk.feature() >= 21 || (jdk.feature() == 17 && jdk.update() >= 17));
        for (boolean throughConnectTunnel : new boolean[]{false, true}) {
            String where = throughConnectTunnel ? "through a CONNECT tunnel" : "direct";
            HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .sslContext(MockServerCaTrustTestSupport.caTrustingSslContext())
                .connectTimeout(Duration.ofSeconds(10));
            if (throughConnectTunnel) {
                builder.proxy(ProxySelector.of(new InetSocketAddress("localhost", mockServer.getLocalPort())));
            }
            HttpClient jdkClient = builder.build();

            CompletableFuture<HttpResponse<String>> response = jdkClient.sendAsync(
                java.net.http.HttpRequest.newBuilder(URI.create("https://localhost:" + mockServer.getLocalPort() + "/processing")).build(),
                HttpResponse.BodyHandlers.ofString()
            );

            ExecutionException failure = assertThrows(where, ExecutionException.class, () -> response.get(10, TimeUnit.SECONDS));
            assertThat(where, failure.getCause(), instanceOf(IOException.class));
            assertThat(where, failure.getCause().getMessage(), containsString("RST_STREAM"));

            HttpResponse<String> next = jdkClient.send(
                java.net.http.HttpRequest.newBuilder(URI.create("https://localhost:" + mockServer.getLocalPort() + "/served")).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertThat(where, next.version(), is(HttpClient.Version.HTTP_2));
            assertThat(where, next.body(), is("served"));
        }
    }

    private static Http2TestClient connect(Route route) throws Exception {
        int port = mockServer.getLocalPort();
        switch (route) {
            case H2C:
                return Http2TestClient.h2c(clientGroup, port);
            case TLS:
                return Http2TestClient.tls(clientGroup, port);
            case CONNECT_TLS:
                return Http2TestClient.throughConnect(clientGroup, port, TARGET_HOST, TARGET_PORT);
            default:
                return Http2TestClient.throughSocks5(clientGroup, port, TARGET_HOST, TARGET_PORT, false);
        }
    }

    private static Http2Headers headers(Route route, HttpMethod method, String path) {
        return new DefaultHttp2Headers()
            .method(method.asciiName())
            .scheme(route.tls ? "https" : "http")
            .authority(route.tunnel ? TARGET_HOST + ":" + TARGET_PORT : "localhost:" + mockServer.getLocalPort())
            .path(path);
    }

    private static boolean openConnectionsBecomeNone() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (mockServer.getInboundConnectionCount() != 0) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(20);
        }
        return true;
    }

    private static List<String> uploads() {
        return Arrays.stream(mockServerClient.retrieveRecordedRequests(request().withPath("/served")))
            .map(recorded -> recorded.getBodyAsString() == null ? "" : recorded.getBodyAsString())
            .collect(Collectors.toList());
    }

    /**
     * What MockServer logged since the last reset, other than the requests it received, the responses it returned and
     * the warnings about a final 1xx over HTTP/2.
     */
    private static List<String> warningsAndErrors() {
        return logMessages()
            .filter(message -> !message.startsWith("received request") && !message.startsWith("returning ") && !isFinalInformationalWarning(message))
            .collect(Collectors.toList());
    }

    private static List<String> finalInformationalWarnings() {
        return logMessages().filter(Http2FinalInformationalResponseIntegrationTest::isFinalInformationalWarning).collect(Collectors.toList());
    }

    private static List<Integer> finalInformationalWarningStatuses() {
        return finalInformationalWarnings().stream()
            .map(warning -> Integer.parseInt(warning.replaceAll("(?s).*with the status:\\s*(\\d+).*", "$1")))
            .collect(Collectors.toList());
    }

    private static boolean isFinalInformationalWarning(String message) {
        return message.startsWith("expectation:") && message.contains("answered a request over HTTP/2 with the status:");
    }

    private static Stream<String> logMessages() {
        return Arrays.stream(mockServerClient.retrieveLogMessagesArray(null))
            .map(message -> message.substring(message.indexOf(" - ") + " - ".length()));
    }
}
