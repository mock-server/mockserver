package org.mockserver.netty.integration.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.concurrent.EventExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.netty.MockServer;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.LogEntrySerializer;
import org.mockserver.socket.tls.KeyStoreFactory;
import org.slf4j.event.Level;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A request that has reached MockServer's socket, but not yet been read, when a write to its client fails because the
 * client has reset the connection: MockServer must still read it, record it and match it, and then close.
 * <p>
 * The client asks for a response larger than the sockets' buffers and reads none of it, so MockServer has bytes waiting
 * to be written. With MockServer's event loops held, the client sends its next request and resets the connection, so
 * that when the loops run again the request and the reset are both waiting: the write is tried first, and fails.
 * <p>
 * Over TLS the same can happen as the handshake ends: the client's last handshake message, its request and the reset
 * arrive together, and what MockServer writes as the handshake completes fails before the request is decoded.
 */
public class RequestAfterFailedWriteIntegrationTest {

    private static final byte[] HTTP2_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final int HTTP2_HEADERS = 0x1;
    private static final int HTTP2_SETTINGS = 0x4;
    private static final int HTTP2_WINDOW_UPDATE = 0x8;
    private static final int HTTP2_DEFAULT_WINDOW = 65_535;
    private static final int HTTP2_MAX_WINDOW = Integer.MAX_VALUE;
    private static final int LARGE_BODY_BYTES = 2 * 1024 * 1024;

    private WorkerGroupMockServer mockServer;
    private int port;

    /**
     * Exposes the event loops of the server's accepted connections, so a test can hold them.
     */
    private static final class WorkerGroupMockServer extends MockServer {
        WorkerGroupMockServer(Configuration configuration) {
            super(configuration, 0);
        }

        EventLoopGroup workers() {
            return getEventLoopGroup();
        }
    }

    @Before
    public void startServer() {
        // WARN: the entries asserted on below must reach the event log whatever level the build runs tests at
        mockServer = new WorkerGroupMockServer(configuration().logLevel("WARN").startupWarmup(false).proxySetup(false));
        port = mockServer.getLocalPort();
        byte[] large = new byte[LARGE_BODY_BYTES];
        Arrays.fill(large, (byte) 'x');
        ExpectationSerializer serializer = new ExpectationSerializer(new MockServerLogger());
        controlPlane("/mockserver/expectation", serializer.serialize(
            new Expectation(request().withPath("/large")).thenRespond(response().withBody(new String(large, StandardCharsets.US_ASCII)))
        ));
        controlPlane("/mockserver/expectation", serializer.serialize(
            // once: the request read after the failed write uses it up, so it was matched
            new Expectation(request().withPath("/next"), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response().withBody("answered"))
        ));
    }

    @After
    public void stopServer() {
        stopQuietly(mockServer);
    }

    @Test
    public void shouldReceiveAnHttp1RequestOnADirectConnection() throws Exception {
        try (Socket socket = connect()) {
            socket.setSoTimeout(10_000);
            sendBehindAStalledResponseThenReset(socket, socket, http1Get("/large"), http1Get("/next"));
        }
    }

    /**
     * Pipelined behind a response that is still being written: one request held by the in-order pipelining gate, with
     * reads paused, and one still unread in the socket when the client resets. Both are received once.
     */
    @Test
    public void shouldReceiveHttp1RequestsPipelinedBehindAStalledResponseWhenTheClientResets() throws Exception {
        byte[] large = new byte[LARGE_BODY_BYTES];
        Arrays.fill(large, (byte) 'x');
        ExpectationSerializer serializer = new ExpectationSerializer(new MockServerLogger());
        // delayed, so the request pipelined behind it is held and the response is written while it is
        controlPlane("/mockserver/expectation", serializer.serialize(
            new Expectation(request().withPath("/delayedLarge")).thenRespond(response()
                .withBody(new String(large, StandardCharsets.US_ASCII))
                .withDelay(TimeUnit.MILLISECONDS, 200))
        ));
        for (String path : new String[]{"/held", "/unread"}) {
            controlPlane("/mockserver/expectation", serializer.serialize(
                new Expectation(request().withPath(path), Times.once(), TimeToLive.unlimited(), 0).thenRespond(response().withBody("answered"))
            ));
        }

        try (Socket socket = connect()) {
            List<String> loggedBefore = loggedAtWarnOrAbove();
            ByteArrayOutputStream pipelined = new ByteArrayOutputStream();
            write(pipelined, http1Get("/delayedLarge"));
            write(pipelined, http1Get("/held"));
            socket.getOutputStream().write(pipelined.toByteArray());
            socket.getOutputStream().flush();
            await("the first request was received", () -> requestsReceived("/delayedLarge") == 1);
            awaitStalled(socket.getInputStream());

            Runnable release = holdEventLoops();
            try {
                socket.getOutputStream().write(http1Get("/unread"));
                socket.getOutputStream().flush();
                socket.setSoLinger(true, 0);
                socket.close();
                // loopback delivers the request and the reset before the loops run again
                Thread.sleep(300);
            } finally {
                release.run();
            }

            awaitReceivedOnce("/held");
            awaitReceivedOnce("/unread");
            await("every connection closed", () -> mockServer.getInboundConnectionCount() == 0);
            assertThat("nothing logged at WARN or above for a client that reset", loggedAtWarnOrAbove(), is(loggedBefore));
        }
    }

    @Test
    public void shouldReceiveAnHttp1RequestThroughAConnectTunnel() throws Exception {
        try (Socket socket = connect()) {
            socket.setSoTimeout(10_000);
            openConnectTunnel(socket);
            sendBehindAStalledResponseThenReset(socket, socket, http1Get("/large"), http1Get("/next"));
        }
    }

    @Test
    public void shouldReceiveAnH2cRequestOnADirectConnection() throws Exception {
        try (Socket socket = connect()) {
            socket.setSoTimeout(10_000);
            sendBehindAStalledResponseThenReset(socket, socket, http2PrefaceAndGet("http", "/large"), http2Get(3, "http", "/next"));
        }
    }

    @Test
    public void shouldReceiveAnH2cRequestThroughAConnectTunnel() throws Exception {
        try (Socket socket = connect()) {
            socket.setSoTimeout(10_000);
            openConnectTunnel(socket);
            sendBehindAStalledResponseThenReset(socket, socket, http2PrefaceAndGet("http", "/large"), http2Get(3, "http", "/next"));
        }
    }

    @Test
    public void shouldReceiveAnH2RequestOnADirectTlsConnection() throws Exception {
        try (Socket socket = connect()) {
            socket.setSoTimeout(10_000);
            SSLSocket tls = startTlsWithH2(socket);
            sendBehindAStalledResponseThenReset(socket, tls, http2PrefaceAndGet("https", "/large"), http2Get(3, "https", "/next"));
        }
    }

    @Test
    public void shouldReceiveAnH2RequestThroughAConnectTunnelWithTls() throws Exception {
        try (Socket socket = connect()) {
            socket.setSoTimeout(10_000);
            openConnectTunnel(socket);
            SSLSocket tls = startTlsWithH2(socket);
            sendBehindAStalledResponseThenReset(socket, tls, http2PrefaceAndGet("https", "/large"), http2Get(3, "https", "/next"));
        }
    }

    /**
     * Received before reads went on after a failed write too, unlike through a tunnel: the direct case of the probe,
     * kept as a guard.
     */
    @Test
    public void shouldReceiveAnH2RequestSentWithTheLastTlsHandshakeMessageOnADirectConnection() throws Exception {
        try (Socket socket = connect()) {
            socket.setSoTimeout(10_000);
            sendWithTheLastHandshakeMessageThenReset(socket);
        }
    }

    @Test
    public void shouldReceiveAnH2RequestSentWithTheLastTlsHandshakeMessageThroughAConnectTunnel() throws Exception {
        try (Socket socket = connect()) {
            socket.setSoTimeout(10_000);
            openConnectTunnel(socket);
            sendWithTheLastHandshakeMessageThenReset(socket);
        }
    }

    @Test
    public void shouldReceiveAnH2RequestSentWithTheLastTlsHandshakeMessageThroughASocksTunnel() throws Exception {
        try (Socket socket = connect()) {
            socket.setSoTimeout(10_000);
            openSocks5Tunnel(socket);
            sendWithTheLastHandshakeMessageThenReset(socket);
        }
    }

    /**
     * A TLS 1.3 client's last handshake flight (its Finished) needs nothing more from the server, so it is held back
     * and sent, with the request, while MockServer's event loops are held.
     */
    private void sendWithTheLastHandshakeMessageThenReset(Socket raw) throws Exception {
        List<String> loggedBefore = loggedAtWarnOrAbove();
        // MockServer's own client context is TLS 1.2 only, whose client cannot send a request with its last flight
        SSLContext tls13 = SSLContext.getInstance("TLSv1.3");
        tls13.init(null, InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
        SSLEngine engine = tls13.createSSLEngine("127.0.0.1", port);
        engine.setUseClientMode(true);
        SSLParameters parameters = engine.getSSLParameters();
        parameters.setProtocols(new String[]{"TLSv1.3"});
        parameters.setApplicationProtocols(new String[]{"h2"});
        engine.setSSLParameters(parameters);
        EngineClient client = new EngineClient(raw, engine);
        ByteArrayOutputStream lastFlightAndRequest = new ByteArrayOutputStream();
        write(lastFlightAndRequest, client.handshakeUpToTheLastFlight());
        assertThat("ALPN", engine.getApplicationProtocol(), is("h2"));
        write(lastFlightAndRequest, client.wrap(http2PrefaceAndGet("https", "/next")));

        Runnable release = holdEventLoops();
        try {
            raw.getOutputStream().write(lastFlightAndRequest.toByteArray());
            raw.getOutputStream().flush();
            raw.setSoLinger(true, 0);
            raw.close();
            // loopback delivers the bytes and the reset before the loops run again
            Thread.sleep(300);
        } finally {
            release.run();
        }

        awaitReceivedOnce("/next");
        await("every connection closed", () -> mockServer.getInboundConnectionCount() == 0);
        assertThat("nothing logged at WARN or above for a client that reset", loggedAtWarnOrAbove(), is(loggedBefore));
    }

    /**
     * A client socket with a small receive buffer, set before it connects so that the kernel does not grow it: the large
     * response then fills it, and the server's send buffer, with most of the response still to write.
     */
    private Socket connect() throws IOException {
        Socket socket = new Socket();
        socket.setReceiveBufferSize(4096);
        socket.connect(new InetSocketAddress("127.0.0.1", port), 10_000);
        socket.setSoTimeout(10_000);
        return socket;
    }

    /**
     * @param raw    the client's TCP socket, which is reset; closed directly, so that a TLS client sends no
     *               {@code close_notify} first
     * @param client the socket the requests are written to: {@code raw}, or a TLS socket layered on it
     */
    private void sendBehindAStalledResponseThenReset(Socket raw, Socket client, byte[] first, byte[] next) throws Exception {
        List<String> loggedBefore = loggedAtWarnOrAbove();
        client.getOutputStream().write(first);
        client.getOutputStream().flush();
        await("the first request was received", () -> requestsReceived("/large") == 1);
        awaitStalled(raw.getInputStream());

        Runnable release = holdEventLoops();
        try {
            client.getOutputStream().write(next);
            client.getOutputStream().flush();
            raw.setSoLinger(true, 0);
            raw.close();
            // loopback delivers the request and the reset before the loops run again
            Thread.sleep(300);
        } finally {
            release.run();
        }

        awaitReceivedOnce("/next");
        assertThat("the first request was received once", requestsReceived("/large"), is(1));
        await("every connection closed", () -> mockServer.getInboundConnectionCount() == 0);
        assertThat("nothing logged at WARN or above for a client that reset", loggedAtWarnOrAbove(), is(loggedBefore));
    }

    private void awaitReceivedOnce(String path) throws InterruptedException {
        // waited for without failing, so that a failure reports how many were received
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (requestsReceived(path) < 1 && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        // and a little longer, for a second copy to show
        Thread.sleep(200);
        assertThat("the request sent before the reset was received, once", requestsReceived(path), is(1));
        assertThat("the request was matched, using up its expectation", statusOfAnotherRequest(path), is(404));
    }

    private int statusOfAnotherRequest(String path) {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String statusLine = new String(readBytes(socket.getInputStream(), 12), StandardCharsets.US_ASCII);
            return Integer.parseInt(statusLine.substring(9, 12));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Waits until the bytes the client has been sent stop growing: its receive buffer is full and MockServer has the
     * rest of the response still to write.
     */
    private static void awaitStalled(InputStream raw) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int before = -1;
        while (System.nanoTime() < deadline) {
            Thread.sleep(250);
            int now = raw.available();
            if (now > 0 && now == before) {
                return;
            }
            before = now;
        }
        throw new AssertionError("the response did not stall within 10 seconds");
    }

    /**
     * Holds every event loop of the accepted connections until the returned action runs.
     */
    private Runnable holdEventLoops() throws InterruptedException {
        List<EventExecutor> loops = new ArrayList<>();
        mockServer.workers().forEach(loops::add);
        CountDownLatch held = new CountDownLatch(loops.size());
        CountDownLatch release = new CountDownLatch(1);
        for (EventExecutor loop : loops) {
            loop.execute(() -> {
                held.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        if (!held.await(10, TimeUnit.SECONDS)) {
            release.countDown();
            throw new AssertionError("the event loops were not held within 10 seconds");
        }
        return release::countDown;
    }

    private byte[] http1Get(String path) {
        return ("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * The client preface; SETTINGS opening each stream's window as far as it goes, and a WINDOW_UPDATE opening the
     * connection's, so that the response is held back by the sockets and not by flow control; and a GET on stream 1.
     */
    private byte[] http2PrefaceAndGet(String scheme, String path) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, HTTP2_PREFACE);
        // SETTINGS_INITIAL_WINDOW_SIZE (0x4)
        writeFrame(out, HTTP2_SETTINGS, 0x0, 0, new byte[]{0x0, 0x4, 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff});
        writeFrame(out, HTTP2_WINDOW_UPDATE, 0x0, 0, int32(HTTP2_MAX_WINDOW - HTTP2_DEFAULT_WINDOW));
        write(out, http2Get(1, scheme, path));
        return out.toByteArray();
    }

    /**
     * HEADERS ending the stream, encoded with no dynamic table: indexed ":method: GET" and ":scheme", and literal
     * ":path" and ":authority".
     */
    private byte[] http2Get(int streamId, String scheme, String path) {
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        headers.write(0x82);
        headers.write("https".equals(scheme) ? 0x87 : 0x86);
        writeLiteralHeader(headers, 0x04, path);
        writeLiteralHeader(headers, 0x01, "127.0.0.1:" + port);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // END_STREAM and END_HEADERS
        writeFrame(out, HTTP2_HEADERS, 0x5, streamId, headers.toByteArray());
        return out.toByteArray();
    }

    private static void writeLiteralHeader(ByteArrayOutputStream headers, int staticTableNameIndex, String value) {
        headers.write(staticTableNameIndex);
        headers.write(value.length());
        write(headers, value.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeFrame(ByteArrayOutputStream out, int type, int flags, int streamId, byte[] payload) {
        out.write(payload.length >> 16);
        out.write(payload.length >> 8);
        out.write(payload.length);
        out.write(type);
        out.write(flags);
        write(out, int32(streamId));
        write(out, payload);
    }

    private static byte[] int32(int value) {
        return new byte[]{(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
    }

    private static void write(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    private void openConnectTunnel(Socket socket) throws IOException {
        socket.getOutputStream().write(("CONNECT 127.0.0.1:" + port + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        while (!head.toString(StandardCharsets.US_ASCII.name()).endsWith("\r\n\r\n")) {
            int read = socket.getInputStream().read();
            if (read == -1) {
                throw new IOException("connection closed during the CONNECT response");
            }
            head.write(read);
        }
        assertThat(head.toString(StandardCharsets.US_ASCII.name()), containsString("200"));
    }

    private SSLSocket startTlsWithH2(Socket socket) throws IOException {
        SSLSocket tls = (SSLSocket) new KeyStoreFactory(configuration(), new MockServerLogger()).sslContext().getSocketFactory()
            .createSocket(socket, "127.0.0.1", port, true);
        tls.setUseClientMode(true);
        SSLParameters parameters = tls.getSSLParameters();
        parameters.setApplicationProtocols(new String[]{"h2"});
        tls.setSSLParameters(parameters);
        tls.startHandshake();
        assertThat("ALPN", tls.getApplicationProtocol(), is("h2"));
        return tls;
    }

    /**
     * Reads the whole of each reply.
     */
    private void openSocks5Tunnel(Socket socket) throws IOException {
        OutputStream output = socket.getOutputStream();
        // version 5, one method offered: no authentication
        output.write(new byte[]{5, 1, 0});
        output.flush();
        assertThat(readBytes(socket.getInputStream(), 2), is(new byte[]{5, 0}));
        // version 5, CONNECT, reserved, IPv4 127.0.0.1, port
        output.write(new byte[]{5, 1, 0, 1, 127, 0, 0, 1, (byte) (port >> 8), (byte) port});
        output.flush();
        // version, status, reserved, address type, and the first byte of the address
        byte[] reply = readBytes(socket.getInputStream(), 5);
        assertThat("SOCKS5 success", reply[1], is((byte) 0));
        int restOfAddress = reply[3] == 3 ? reply[4] : reply[3] == 1 ? 3 : 15;
        readBytes(socket.getInputStream(), restOfAddress + 2);
    }

    private static byte[] readBytes(InputStream input, int length) throws IOException {
        byte[] bytes = new byte[length];
        for (int read = 0; read < length; ) {
            int count = input.read(bytes, read, length - read);
            if (count == -1) {
                throw new IOException("connection closed after " + read + " of " + length + " bytes");
            }
            read += count;
        }
        return bytes;
    }

    /**
     * A TLS client over a blocking socket, driven by hand so that what it sends can be held back.
     */
    private static final class EngineClient {
        private static final ByteBuffer NOTHING = ByteBuffer.allocate(0);
        private final Socket socket;
        private final SSLEngine engine;
        private final ByteBuffer received;

        EngineClient(Socket socket, SSLEngine engine) {
            this.socket = socket;
            this.engine = engine;
            this.received = ByteBuffer.allocate(engine.getSession().getPacketBufferSize() * 2);
            received.flip();
        }

        /**
         * Sends what the handshake needs until the server has been heard, and returns what the client sends after
         * that, which completes the handshake, unsent.
         */
        byte[] handshakeUpToTheLastFlight() throws Exception {
            engine.beginHandshake();
            ByteArrayOutputStream lastFlight = new ByteArrayOutputStream();
            ByteBuffer appIn = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
            boolean serverHeard = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                SSLEngineResult.HandshakeStatus status = engine.getHandshakeStatus();
                if (status == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                    byte[] out = wrapOnce(NOTHING);
                    if (serverHeard) {
                        write(lastFlight, out);
                    } else {
                        socket.getOutputStream().write(out);
                        socket.getOutputStream().flush();
                    }
                } else if (status == SSLEngineResult.HandshakeStatus.NEED_UNWRAP || status == SSLEngineResult.HandshakeStatus.NEED_UNWRAP_AGAIN) {
                    appIn.clear();
                    SSLEngineResult result = engine.unwrap(received, appIn);
                    if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        receive();
                    } else {
                        serverHeard = true;
                    }
                } else if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                    for (Runnable task; (task = engine.getDelegatedTask()) != null; ) {
                        task.run();
                    }
                } else {
                    assertThat("the server was heard before the handshake completed", serverHeard, is(true));
                    return lastFlight.toByteArray();
                }
            }
            throw new AssertionError("the TLS handshake did not reach its last flight within 10 seconds");
        }

        byte[] wrap(byte[] application) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteBuffer in = ByteBuffer.wrap(application);
            while (in.hasRemaining()) {
                write(out, wrapOnce(in));
            }
            return out.toByteArray();
        }

        private byte[] wrapOnce(ByteBuffer in) throws IOException {
            ByteBuffer out = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
            SSLEngineResult result = engine.wrap(in, out);
            assertThat("wrap", result.getStatus(), is(SSLEngineResult.Status.OK));
            out.flip();
            byte[] bytes = new byte[out.remaining()];
            out.get(bytes);
            return bytes;
        }

        private void receive() throws IOException {
            received.compact();
            int count = socket.getInputStream().read(received.array(), received.arrayOffset() + received.position(), received.remaining());
            if (count == -1) {
                throw new IOException("connection closed during the TLS handshake");
            }
            received.position(received.position() + count);
            received.flip();
        }
    }

    private int requestsReceived(String path) {
        String received = controlPlane("/mockserver/retrieve?type=REQUESTS&format=JSON", "{\"path\":\"" + path + "\"}");
        try {
            return received.trim().isEmpty() ? 0 : new ObjectMapper().readTree(received).size();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private List<String> loggedAtWarnOrAbove() {
        List<String> messages = new ArrayList<>();
        for (LogEntry entry : new LogEntrySerializer(new MockServerLogger()).deserializeArray(controlPlane("/mockserver/retrieve?type=LOGS&format=LOG_ENTRIES", ""))) {
            // a tunnel's loopback logs the notice that it trusts any certificate, whatever its client does
            if (entry.getLogLevel() != null && entry.getLogLevel().toInt() >= Level.WARN.toInt()
                && !String.valueOf(entry.getMessageFormat()).contains("configured to trust ALL X.509 certificates")) {
                messages.add(entry.getLogLevel() + " " + entry.getMessageFormat());
            }
        }
        return messages;
    }

    /**
     * One control-plane call on a connection of its own, closed with its response.
     */
    private String controlPlane(String pathAndQuery, String json) {
        try (Socket socket = connect()) {
            socket.setSoTimeout(10_000);
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            OutputStream output = socket.getOutputStream();
            output.write(("PUT " + pathAndQuery + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nContent-Type: application/json\r\nContent-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int count; (count = socket.getInputStream().read(buffer)) != -1; ) {
                response.write(buffer, 0, count);
            }
            String text = response.toString(StandardCharsets.UTF_8.name());
            assertThat(text, startsWith("HTTP/1.1 20"));
            return text.substring(text.indexOf("\r\n\r\n") + 4);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void await(String reason, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(reason, condition.getAsBoolean(), is(true));
    }
}
