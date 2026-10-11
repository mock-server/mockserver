package org.mockserver.netty.connection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.metrics.Metrics;
import org.mockserver.model.Delay;
import org.mockserver.netty.MockServer;
import org.mockserver.netty.MockServerCaTrustTestSupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.connection.RecordedDurations.HANDLER;
import static org.mockserver.netty.connection.RecordedDurations.READ_TO_DECODE;
import static org.mockserver.netty.connection.RecordedDurations.TRANSPORT;
import static org.mockserver.netty.connection.RecordedDurations.count;
import static org.mockserver.netty.connection.RecordedDurations.countOver;
import static org.mockserver.netty.connection.RecordedDurations.sum;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * {@code mock_server_request_transport_duration_seconds} over real sockets: time MockServer spends after
 * its request handler returns (a chunk-delayed body, a client that will not read) is in the transport
 * histogram and not in the handler histogram, HTTP/2 streams are timed one by one, and a raw-bytes
 * response does not shift the timing of the keep-alive requests after it. The read-to-decode histogram
 * ({@code mock_server_request_read_to_decode_duration_seconds}) takes the wait for a head split across reads, which
 * the transport histogram does not.
 */
public class RequestTransportDurationIntegrationTest {

    private static final double HALF_SECOND = 0.5;

    private MockServer mockServer;
    private MockServerClient mockServerClient;
    private int port;

    @Before
    public void startServer() {
        Metrics.resetAdditionalMetricsForTesting();
        mockServer = new MockServer(configuration()
            .metricsEnabled(true)
            // room for the slow-reader expectation's 16 MiB body
            .maxRequestBodySize(64 * 1024 * 1024)
            .startupWarmup(false)
            .proxySetup(false)
            .dynamicallyCreateCertificateAuthorityCertificate(false), 0);
        port = mockServer.getLocalPort();
        mockServerClient = new MockServerClient("localhost", port);
    }

    @After
    public void stopServer() {
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
        Metrics.resetAdditionalMetricsForTesting();
    }

    @Test
    public void shouldTimeAChunkDelayedBodyInTheTransportHistogramOnly() throws Exception {
        mockServerClient.when(request().withPath("/chunked")).respond(response()
            .withBody("0123456789abcdefghijklmnopqrst")
            .withConnectionOptions(connectionOptions().withChunkSize(10).withChunkDelay(new Delay(TimeUnit.MILLISECONDS, 250))));
        settle();
        long transportBefore = count(TRANSPORT);
        long transportSlowBefore = countOver(TRANSPORT, HALF_SECOND);
        long handlerSlowBefore = countOver(HANDLER, HALF_SECOND);

        HttpResponse<String> response = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().send(
            java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/chunked")).timeout(Duration.ofSeconds(10)).build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.body(), is("0123456789abcdefghijklmnopqrst"));
        awaitTrue(() -> count(TRANSPORT) > transportBefore);
        assertThat("the last chunk left ~750 ms after the request", countOver(TRANSPORT, HALF_SECOND) - transportSlowBefore, is(1L));
        assertThat("the handler handed the response off at once", countOver(HANDLER, HALF_SECOND) - handlerSlowBefore, is(0L));
    }

    @Test
    public void shouldTimeAClientThatWillNotReadInTheTransportHistogramOnly() throws Exception {
        int bodyBytes = 16 * 1024 * 1024;
        mockServerClient.when(request().withPath("/large")).respond(response().withBody("x".repeat(bodyBytes)));
        settle();
        long transportBefore = count(TRANSPORT);
        long transportSlowBefore = countOver(TRANSPORT, HALF_SECOND);
        long handlerSlowBefore = countOver(HANDLER, HALF_SECOND);

        try (Socket socket = new Socket()) {
            socket.setReceiveBufferSize(4096);
            socket.connect(new InetSocketAddress("localhost", port), 10_000);
            socket.setSoTimeout(10_000);
            OutputStream output = socket.getOutputStream();
            output.write(("GET /large HTTP/1.1\r\nHost: localhost:" + port + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.flush();

            Thread.sleep(1_000);
            assertThat("a body far larger than the socket buffers cannot have been written yet", count(TRANSPORT), is(transportBefore));

            assertThat(readResponseBytes(socket.getInputStream(), bodyBytes), greaterThanOrEqualTo((long) bodyBytes));
        }
        awaitTrue(() -> count(TRANSPORT) > transportBefore);
        assertThat(count(TRANSPORT) - transportBefore, is(1L));
        assertThat(countOver(TRANSPORT, HALF_SECOND) - transportSlowBefore, is(1L));
        assertThat(countOver(HANDLER, HALF_SECOND) - handlerSlowBefore, is(0L));
    }

    @Test
    public void shouldTimeAKeepAliveRequestAfterARawBytesResponseFromItsOwnStart() throws Exception {
        mockServerClient.when(request().withPath("/raw")).error(error()
            .withResponseBytes("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nraw".getBytes(StandardCharsets.UTF_8)));
        mockServerClient.when(request().withPath("/normal")).respond(response().withBody("normal"));
        settle();
        long transportBefore = count(TRANSPORT);
        long transportSlowBefore = countOver(TRANSPORT, HALF_SECOND);

        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(10_000);
            assertThat(exchange(socket, "/raw"), endsWith("raw"));

            Thread.sleep(1_000);

            assertThat(exchange(socket, "/normal"), endsWith("normal"));
        }
        awaitTrue(() -> count(TRANSPORT) > transportBefore);
        Thread.sleep(200);
        assertThat("only the normal response is recorded", count(TRANSPORT) - transportBefore, is(1L));
        assertThat("timed from its own request, not from the raw-bytes request a second earlier", countOver(TRANSPORT, HALF_SECOND) - transportSlowBefore, is(0L));
    }

    @Test
    public void shouldTimeAKeepAliveRequestAfterADelayedRawBytesResponseFromItsOwnStart() throws Exception {
        mockServerClient.when(request().withPath("/raw-delayed")).error(error()
            .withResponseBytes("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nraw".getBytes(StandardCharsets.UTF_8))
            .withDelay(TimeUnit.MILLISECONDS, 600));
        mockServerClient.when(request().withPath("/normal")).respond(response().withBody("normal"));
        settle();
        long transportBefore = count(TRANSPORT);
        long transportSlowBefore = countOver(TRANSPORT, HALF_SECOND);

        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(10_000);
            assertThat(exchange(socket, "/raw-delayed"), endsWith("raw"));
            // straight away: the raw bytes are written off the event loop, so the next request races the end of their exchange
            assertThat(exchange(socket, "/normal"), endsWith("normal"));
        }
        awaitTrue(() -> count(TRANSPORT) > transportBefore);
        Thread.sleep(200);
        assertThat(count(TRANSPORT) - transportBefore, is(1L));
        assertThat("timed from its own request, not from the delayed raw-bytes request", countOver(TRANSPORT, HALF_SECOND) - transportSlowBefore, is(0L));
    }

    @Test
    public void shouldWriteALargeRawBytesResponseToASlowReaderAndKeepTheConnectionInStep() throws Exception {
        int bodyBytes = 1024 * 1024;
        byte[] head = ("HTTP/1.1 200 OK\r\nContent-Length: " + bodyBytes + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] rawResponse = Arrays.copyOf(head, head.length + bodyBytes);
        Arrays.fill(rawResponse, head.length, rawResponse.length, (byte) 'x');
        mockServerClient.when(request().withPath("/raw-large")).error(error().withResponseBytes(rawResponse));
        mockServerClient.when(request().withPath("/normal")).respond(response().withBody("normal"));
        settle();
        long transportBefore = count(TRANSPORT);

        try (Socket socket = new Socket()) {
            socket.setReceiveBufferSize(4096);
            socket.connect(new InetSocketAddress("localhost", port), 10_000);
            socket.setSoTimeout(10_000);
            assertThat(exchange(socket, "/raw-large").length(), is(rawResponse.length));
            assertThat("the next response on the connection is its own, with nothing stray before it",
                exchange(socket, "/normal"), allOf(startsWith("HTTP/1.1 200"), endsWith("normal")));
        }
        awaitTrue(() -> count(TRANSPORT) > transportBefore);
        Thread.sleep(200);
        assertThat("only the normal response is recorded", count(TRANSPORT) - transportBefore, is(1L));
    }

    @Test
    public void shouldTimeAHeadSplitAcrossReadsInTheReadToDecodeHistogramOnly() throws Exception {
        mockServerClient.when(request().withPath("/split")).respond(response().withBody("split"));
        mockServerClient.when(request().withPath("/normal")).respond(response().withBody("normal"));
        settle();
        long readBefore = count(READ_TO_DECODE);
        long readSlowBefore = countOver(READ_TO_DECODE, HALF_SECOND);
        double readSumBefore = sum(READ_TO_DECODE);
        long transportBefore = count(TRANSPORT);
        long transportSlowBefore = countOver(TRANSPORT, HALF_SECOND);

        double exchangeSeconds;
        double splitRequestSeconds;
        try (Socket socket = new Socket("localhost", port)) {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(10_000);
            OutputStream output = socket.getOutputStream();
            long exchangeStart = System.nanoTime();
            output.write("GET /split HTTP/1.1\r\nHo".getBytes(StandardCharsets.UTF_8));
            output.flush();
            Thread.sleep(600);
            output.write(("st: localhost:" + port + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
            assertThat(readResponse(socket), endsWith("split"));
            exchangeSeconds = (System.nanoTime() - exchangeStart) / 1_000_000_000.0;
            // recorded when the head was decoded, before the response was written
            splitRequestSeconds = sum(READ_TO_DECODE) - readSumBefore;

            Thread.sleep(600);
            assertThat(exchange(socket, "/normal"), endsWith("normal"));
        }
        awaitTrue(() -> count(TRANSPORT) - transportBefore >= 2);
        assertThat(count(READ_TO_DECODE) - readBefore, is(2L));
        assertThat("the split head waited ~600 ms for its second read; the keep-alive request after the pause did not",
            countOver(READ_TO_DECODE, HALF_SECOND) - readSlowBefore, is(1L));
        assertThat("the split request's span fits inside the exchange the client saw",
            splitRequestSeconds, allOf(greaterThanOrEqualTo(0.5), lessThanOrEqualTo(exchangeSeconds)));
        assertThat(count(TRANSPORT) - transportBefore, is(2L));
        assertThat("the transport span starts after the head is decoded", countOver(TRANSPORT, HALF_SECOND) - transportSlowBefore, is(0L));
    }

    @Test
    public void shouldTimeEachHttp2StreamSeparately() throws Exception {
        mockServerClient.when(request().withPath("/h2-fast")).respond(response().withBody("fast"));
        mockServerClient.when(request().withPath("/h2-slow")).respond(response().withBody("slow").withDelay(TimeUnit.MILLISECONDS, 600));
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .sslContext(MockServerCaTrustTestSupport.caTrustingSslContext())
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        settle();
        long transportBefore = count(TRANSPORT);
        long transportSlowBefore = countOver(TRANSPORT, HALF_SECOND);

        CompletableFuture<HttpResponse<String>> slow = httpClient.sendAsync(h2Request("/h2-slow"), HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> fast = httpClient.sendAsync(h2Request("/h2-fast"), HttpResponse.BodyHandlers.ofString());

        assertThat(fast.get(10, TimeUnit.SECONDS).version(), is(HttpClient.Version.HTTP_2));
        assertThat(fast.get().body(), is("fast"));
        assertThat(slow.get(10, TimeUnit.SECONDS).body(), is("slow"));
        awaitTrue(() -> count(TRANSPORT) - transportBefore >= 2);
        assertThat(count(TRANSPORT) - transportBefore, is(2L));
        assertThat("only the delayed stream is slow", countOver(TRANSPORT, HALF_SECOND) - transportSlowBefore, is(1L));
    }

    private java.net.http.HttpRequest h2Request(String path) {
        return java.net.http.HttpRequest.newBuilder(URI.create("https://localhost:" + port + path)).timeout(Duration.ofSeconds(10)).build();
    }

    private String exchange(Socket socket, String path) throws IOException {
        OutputStream output = socket.getOutputStream();
        output.write(("GET " + path + " HTTP/1.1\r\nHost: localhost:" + port + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
        return readResponse(socket);
    }

    private String readResponse(Socket socket) throws IOException {
        InputStream input = socket.getInputStream();
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        while (!head.toString(StandardCharsets.UTF_8).endsWith("\r\n\r\n")) {
            int next = input.read();
            if (next == -1) {
                throw new IOException("connection closed while reading response head: " + head);
            }
            head.write(next);
        }
        int contentLength = 0;
        for (String line : head.toString(StandardCharsets.UTF_8).split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
            }
        }
        return head.toString(StandardCharsets.UTF_8) + new String(input.readNBytes(contentLength), StandardCharsets.UTF_8);
    }

    private static long readResponseBytes(InputStream input, int bodyBytes) throws Exception {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int count;
        while (total < bodyBytes && (count = input.read(buffer)) != -1) {
            total += count;
            if (head.size() < 256) {
                head.write(buffer, 0, Math.min(count, 256));
            }
        }
        assertThat(head.toString(StandardCharsets.UTF_8).startsWith("HTTP/1.1 200"), is(true));
        return total;
    }

    // the expectation PUTs are recorded as their write completes, which can land just after the client sees the reply
    private static void settle() throws InterruptedException {
        long last;
        do {
            last = count(TRANSPORT);
            Thread.sleep(200);
        } while (count(TRANSPORT) != last);
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }
}
