package org.mockserver.netty.integration.mock;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.http.TcpChaosRegistry;
import org.mockserver.model.TcpChaosProfile;
import org.mockserver.netty.MockServer;
import org.mockserver.socket.tls.KeyStoreFactory;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.TcpChaosProfile.tcpChaosProfile;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.testing.tls.SSLSocketFactory.sslSocketFactory;

/**
 * Real-socket proof that the TCP chaos faults {@code resetPeer} and {@code resetMidResponse} reach the client as a
 * TCP RST ("connection reset"), not a clean close, {@code resetMidResponse} only after the whole response: over plain
 * HTTP, over TLS (no {@code close_notify} before the RST) and through CONNECT and SOCKS tunnels, where the request is
 * served on MockServer's loopback leg.
 */
public class ChaosConnectionResetIntegrationTest {

    private static MockServer mockServer;
    private static MockServerClient mockServerClient;

    @BeforeClass
    public static void startServer() {
        mockServer = new MockServer();
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());
    }

    @AfterClass
    public static void stopServer() {
        TcpChaosRegistry.getInstance().reset();
        stopQuietly(mockServerClient);
        stopQuietly(mockServer);
    }

    @Before
    public void seedExpectation() {
        TcpChaosRegistry.getInstance().reset();
        mockServerClient.reset();
        mockServerClient.when(request().withPath("/ok")).respond(response().withBody("hello-world"));
    }

    @After
    public void clearChaos() {
        TcpChaosRegistry.getInstance().reset();
    }

    // ----- resetPeer: keyed on the connection's remote host -----

    @Test(timeout = 30000)
    public void shouldResetPeerOverHttp() throws Exception {
        TcpChaosRegistry.getInstance().put("127.0.0.1", tcpChaosProfile().withResetPeer(true));
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            assertConnectionReset(socket);
        }
    }

    @Test(timeout = 30000)
    public void shouldResetPeerOverTlsWithoutCloseNotify() throws Exception {
        TcpChaosRegistry.getInstance().put("127.0.0.1", tcpChaosProfile().withResetPeer(true));
        try (Socket socket = sslSocketFactory().wrapSocket(new Socket("127.0.0.1", mockServer.getLocalPort()))) {
            assertConnectionReset(socket);
        }
    }

    @Test(timeout = 30000)
    public void shouldResetPeerOfTunnelledClient() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            openConnectTunnel(socket);
            resetPeerOnRelayLoopback();
            assertConnectionReset(socket);
        }
    }

    @Test(timeout = 30000)
    public void shouldResetPeerOfTunnelledTlsClient() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            openConnectTunnel(socket);
            resetPeerOnRelayLoopback();
            assertConnectionReset(sslSocketFactory().wrapSocket(socket));
        }
    }

    // ----- resetMidResponse: keyed on the request's Host header -----

    @Test(timeout = 30000)
    public void shouldResetMidResponseOverHttp() throws Exception {
        TcpChaosRegistry.getInstance().put("localhost", tcpChaosProfile().withResetMidResponse(true));
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            assertResponseThenReset(socket);
        }
    }

    @Test(timeout = 30000)
    public void shouldResetMidResponseOverTlsWithoutCloseNotify() throws Exception {
        TcpChaosRegistry.getInstance().put("localhost", tcpChaosProfile().withResetMidResponse(true));
        try (Socket socket = sslSocketFactory().wrapSocket(new Socket("127.0.0.1", mockServer.getLocalPort()))) {
            assertResponseThenReset(socket);
        }
    }

    @Test(timeout = 30000)
    public void shouldResetMidResponseOfTunnelledClient() throws Exception {
        TcpChaosRegistry.getInstance().put("localhost", tcpChaosProfile().withResetMidResponse(true));
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            openConnectTunnel(socket);
            assertResponseThenReset(socket);
        }
    }

    @Test(timeout = 30000)
    public void shouldResetMidResponseOfTunnelledTlsClient() throws Exception {
        TcpChaosRegistry.getInstance().put("localhost", tcpChaosProfile().withResetMidResponse(true));
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            openConnectTunnel(socket);
            assertResponseThenReset(sslSocketFactory().wrapSocket(socket));
        }
    }

    @Test(timeout = 30000)
    public void shouldResetMidResponseOfSocks5TunnelledClient() throws Exception {
        TcpChaosRegistry.getInstance().put("localhost", tcpChaosProfile().withResetMidResponse(true));
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            openSocks5Tunnel(socket);
            assertResponseThenReset(socket);
        }
    }

    @Test(timeout = 30000)
    public void shouldResetMidResponseOfTunnelledHttp2TlsClient() throws Exception {
        TcpChaosRegistry.getInstance().put("localhost", tcpChaosProfile().withResetMidResponse(true));
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            openConnectTunnel(socket);
            assertHttp2ResponseThenReset(socket);
        }
    }

    @Test(timeout = 30000)
    public void shouldResetMidResponseOfSocks5TunnelledHttp2TlsClient() throws Exception {
        TcpChaosRegistry.getInstance().put("localhost", tcpChaosProfile().withResetMidResponse(true));
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            openSocks5Tunnel(socket);
            assertHttp2ResponseThenReset(socket);
        }
    }

    /**
     * Registered once the tunnel is open, so it is the loopback leg, not the client's own connection (accepted before
     * any profile existed), that resets. The relay dials the wildcard address, which reaches MockServer as the IPv4 or IPv6 loopback.
     */
    private static void resetPeerOnRelayLoopback() {
        TcpChaosProfile resetPeer = tcpChaosProfile().withResetPeer(true);
        TcpChaosRegistry.getInstance().put("127.0.0.1", resetPeer);
        TcpChaosRegistry.getInstance().put("0:0:0:0:0:0:0:1", resetPeer);
    }

    private static void openConnectTunnel(Socket socket) throws IOException {
        socket.setSoTimeout(10000);
        String target = "localhost:" + mockServer.getLocalPort();
        socket.getOutputStream().write(("CONNECT " + target + " HTTP/1.1\r\nHost: " + target + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        InputStream in = socket.getInputStream();
        StringBuilder head = new StringBuilder();
        while (!head.toString().endsWith("\r\n\r\n")) {
            int read = in.read();
            if (read == -1) {
                throw new AssertionError("CONNECT response ended early: " + head);
            }
            head.append((char) read);
        }
        assertThat(head.toString(), startsWith("HTTP/1.1 200"));
    }

    private static void openSocks5Tunnel(Socket socket) throws IOException {
        socket.setSoTimeout(10000);
        OutputStream out = socket.getOutputStream();
        DataInputStream in = new DataInputStream(socket.getInputStream());
        out.write(new byte[]{5, 1, 0});
        out.flush();
        byte[] method = new byte[2];
        in.readFully(method);
        assertThat(method[1], is((byte) 0));
        int port = mockServer.getLocalPort();
        out.write(new byte[]{5, 1, 0, 1, 127, 0, 0, 1, (byte) (port >> 8), (byte) port});
        out.flush();
        byte[] reply = new byte[5];
        in.readFully(reply);
        assertThat("SOCKS5 connect must succeed", reply[1], is((byte) 0));
        // the bound address is a domain name: its length byte, the name, then the port
        in.readFully(new byte[(reply[4] & 0xff) + 2]);
    }

    private static void assertConnectionReset(Socket socket) throws IOException {
        assertThat("nothing is answered before the reset", readUntilReset(socket), is(""));
    }

    /**
     * resetMidResponse sends the whole response before the reset, through a tunnel too: its bytes reach the client's
     * receive buffer before the RST, and a read returns them before it reports the reset.
     */
    private static void assertResponseThenReset(Socket socket) throws IOException {
        String received = readUntilReset(socket);
        assertThat(received, startsWith("HTTP/1.1 200"));
        assertThat(received, containsString("hello-world"));
    }

    /**
     * Sends a request and reads until the connection ends, which must be with a reset, never cleanly.
     */
    private static String readUntilReset(Socket socket) throws IOException {
        socket.setSoTimeout(10000);
        socket.getOutputStream().write(("GET /ok HTTP/1.1\r\nHost: localhost:" + mockServer.getLocalPort() + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        InputStream in = socket.getInputStream();
        StringBuilder received = new StringBuilder();
        try {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                received.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
            throw new AssertionError("expected a connection reset but the connection ended cleanly after: " + received);
        } catch (IOException expected) {
            assertThat(describe(expected), containsString("reset"));
        }
        return received.toString();
    }

    /**
     * An h2 client by hand, so that it writes nothing after its request (not even a SETTINGS ack): the reset then
     * can only be read, after every frame sent before it, never met as a failed write.
     */
    private static void assertHttp2ResponseThenReset(Socket tunnel) throws IOException {
        String authority = "localhost:" + mockServer.getLocalPort();
        SSLSocket tls = (SSLSocket) new KeyStoreFactory(configuration(), new MockServerLogger()).sslContext().getSocketFactory()
            .createSocket(tunnel, "localhost", mockServer.getLocalPort(), true);
        SSLParameters parameters = tls.getSSLParameters();
        parameters.setApplicationProtocols(new String[]{"h2"});
        tls.setSSLParameters(parameters);
        tls.startHandshake();
        assertThat(tls.getApplicationProtocol(), is("h2"));

        ByteArrayOutputStream headerBlock = new ByteArrayOutputStream();
        // HPACK: :method GET, :scheme https, then :path and :authority as literals
        headerBlock.write(new byte[]{(byte) 0x82, (byte) 0x87, 0x44, 3});
        headerBlock.write("/ok".getBytes(StandardCharsets.US_ASCII));
        headerBlock.write(new byte[]{0x41, (byte) authority.length()});
        headerBlock.write(authority.getBytes(StandardCharsets.US_ASCII));
        byte[] headers = headerBlock.toByteArray();
        OutputStream out = tls.getOutputStream();
        out.write("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[]{0, 0, 0, 4, 0, 0, 0, 0, 0});
        // HEADERS on stream 1 with END_STREAM and END_HEADERS
        out.write(new byte[]{0, 0, (byte) headers.length, 1, 5, 0, 0, 0, 1});
        out.write(headers);
        out.flush();

        DataInputStream in = new DataInputStream(tls.getInputStream());
        boolean responseHeaders = false;
        StringBuilder data = new StringBuilder();
        try {
            while (true) {
                byte[] frameHeader = new byte[9];
                in.readFully(frameHeader);
                int length = ((frameHeader[0] & 0xff) << 16) | ((frameHeader[1] & 0xff) << 8) | (frameHeader[2] & 0xff);
                int type = frameHeader[3] & 0xff;
                int streamId = ((frameHeader[5] & 0x7f) << 24) | ((frameHeader[6] & 0xff) << 16) | ((frameHeader[7] & 0xff) << 8) | (frameHeader[8] & 0xff);
                byte[] payload = new byte[length];
                in.readFully(payload);
                if (streamId == 1 && type == 1) {
                    responseHeaders = true;
                } else if (streamId == 1 && type == 0) {
                    data.append(new String(payload, StandardCharsets.UTF_8));
                }
            }
        } catch (EOFException cleanEnd) {
            throw new AssertionError("expected a connection reset but the connection ended cleanly after: " + data);
        } catch (IOException expected) {
            assertThat(describe(expected), containsString("reset"));
        }
        assertThat("the response HEADERS arrive before the reset", responseHeaders, is(true));
        assertThat("the response DATA arrive before the reset", data.toString(), is("hello-world"));
    }

    private static String describe(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            messages.append(cause).append(' ');
        }
        return messages.toString().toLowerCase();
    }
}
