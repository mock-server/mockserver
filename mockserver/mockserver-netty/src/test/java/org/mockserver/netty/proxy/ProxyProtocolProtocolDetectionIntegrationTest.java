package org.mockserver.netty.proxy;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpTemplate;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.tls.KeyStoreFactory;

import javax.net.ssl.SSLSocket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.Retries.tryWaitForSuccess;

/**
 * A connection that opens with a PROXY protocol header (v1 text or v2 binary) is, once the header is taken off,
 * detected like any other: HTTP is mocked, TLS is terminated and binary is forwarded. A CONNECT tunnel goes to
 * MockServer itself, not to the header's destination, so the requests in it are mocked or forwarded to the CONNECT
 * target. The client address the header carries is the remote address of the requests that follow it.
 */
public class ProxyProtocolProtocolDetectionIntegrationTest {

    private static final String SOURCE_IP = "10.9.8.7";
    private static final int SOURCE_PORT = 45678;
    private static final String MOCKED_PATH = "/proxy-protocol-mocked";
    private static final String MOCKED_BODY = "mocked for ";

    private static ClientAndServer mockServer;
    private static final String PROXY_USERNAME = "proxy-user";
    private static final String PROXY_PASSWORD = "proxy-password";
    private static ClientAndServer proxyAuthenticatingServer;
    private static ServerSocket binaryUpstream;
    // a header destination that is not a MockServer, and that a tunnel must never reach
    private static CountingListener headerDestination;
    private static ClientAndServer connectTarget;
    private static final String FORWARDED_PATH = "/proxy-protocol-forwarded";
    private static final String FORWARDED_BODY = "from the CONNECT target";
    private static final List<SocketAddress> binaryClientAddresses = new CopyOnWriteArrayList<>();

    @BeforeClass
    public static void startServers() throws IOException {
        binaryUpstream = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(ProxyProtocolProtocolDetectionIntegrationTest::echoEachConnection, "proxy-protocol-binary-upstream");
        acceptor.setDaemon(true);
        acceptor.start();
        mockServer = startClientAndServer(transparentProxyConfiguration(), PortFactory.findFreePort());
        proxyAuthenticatingServer = startClientAndServer(transparentProxyConfiguration().proxyAuthenticationUsername(PROXY_USERNAME).proxyAuthenticationPassword(PROXY_PASSWORD), PortFactory.findFreePort());
        headerDestination = new CountingListener();
        connectTarget = startClientAndServer(PortFactory.findFreePort());
        connectTarget.when(request().withPath(FORWARDED_PATH)).respond(response().withBody(FORWARDED_BODY));
    }

    private static Configuration transparentProxyConfiguration() {
        return configuration()
            .transparentProxyEnabled(true)
            .binaryProxyListener((binaryRequest, binaryResponse, serverAddress, clientAddress) -> binaryClientAddresses.add(clientAddress));
    }

    @AfterClass
    public static void stopServers() throws IOException {
        stopQuietly(mockServer);
        stopQuietly(proxyAuthenticatingServer);
        stopQuietly(connectTarget);
        binaryUpstream.close();
        headerDestination.close();
    }

    @Before
    public void mockOnePath() {
        mockServer.reset();
        binaryClientAddresses.clear();
        // the body names the remote address recorded for the request
        mockServer.when(request().withPath(MOCKED_PATH)).respond(template(HttpTemplate.TemplateType.MUSTACHE,
            "{\"statusCode\": 200, \"body\": \"" + MOCKED_BODY + "{{ request.remoteAddress }}\"}"));
    }

    @Test
    public void shouldMockHttpRequestAfterProxyV1Header() throws Exception {
        shouldMockHttpRequestAfter(proxyV1Header(PortFactory.findFreePort()));
    }

    @Test
    public void shouldMockHttpRequestAfterProxyV2Header() throws Exception {
        shouldMockHttpRequestAfter(proxyV2Header(PortFactory.findFreePort()));
    }

    private void shouldMockHttpRequestAfter(byte[] proxyHeader) throws Exception {
        try (Socket socket = connect()) {
            send(socket, proxyHeader, getRequest());

            assertMockedFromProxySource(readHttpResponse(socket.getInputStream()));
        }
    }

    @Test
    public void shouldMockHttp2CleartextRequestAfterProxyV1Header() throws Exception {
        shouldMockHttp2CleartextRequestAfter(proxyV1Header(PortFactory.findFreePort()));
    }

    @Test
    public void shouldMockHttp2CleartextRequestAfterProxyV2Header() throws Exception {
        shouldMockHttp2CleartextRequestAfter(proxyV2Header(PortFactory.findFreePort()));
    }

    private void shouldMockHttp2CleartextRequestAfter(byte[] proxyHeader) throws Exception {
        try (Socket socket = connect()) {
            send(socket, proxyHeader, "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII), http2Frame(0x4, 0, 0, new byte[0]), http2GetHeaders());

            assertThat(readHttp2Body(socket.getInputStream()), is(MOCKED_BODY + SOURCE_IP + ":" + SOURCE_PORT));
        }
    }

    // A CONNECT is handled by the server the client connected to, which requires proxy credentials, and its tunnel
    // goes to that MockServer, not to the header's destination, which is not a MockServer.
    @Test
    public void shouldOpenConnectTunnelAfterProxyV1Header() throws Exception {
        shouldOpenConnectTunnelAfter(proxyV1Header(headerDestination.port()));
    }

    @Test
    public void shouldOpenConnectTunnelAfterProxyV2Header() throws Exception {
        shouldOpenConnectTunnelAfter(proxyV2Header(headerDestination.port()));
    }

    private void shouldOpenConnectTunnelAfter(byte[] proxyHeader) throws Exception {
        String connect = connectRequest("127.0.0.1:" + PortFactory.findFreePort());
        try (Socket socket = connect(proxyAuthenticatingServer)) {
            send(socket, proxyHeader, (connect + "\r\n").getBytes(StandardCharsets.US_ASCII));
            assertThat(readHttpResponse(socket.getInputStream()), startsWith("HTTP/1.1 407"));
        }
        try (Socket socket = connect(proxyAuthenticatingServer)) {
            String credentials = Base64.getEncoder().encodeToString((PROXY_USERNAME + ":" + PROXY_PASSWORD).getBytes(StandardCharsets.UTF_8));
            send(socket, proxyHeader, (connect + "Proxy-Authorization: Basic " + credentials + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            assertThat(readHttpResponse(socket.getInputStream()), startsWith("HTTP/1.1 200"));
        }
        assertThat(headerDestination.connections(), is(0));
    }

    @Test
    public void shouldMockRequestInConnectTunnelAfterProxyV1Header() throws Exception {
        shouldMockRequestInConnectTunnelAfter(proxyV1Header(headerDestination.port()));
    }

    @Test
    public void shouldMockRequestInConnectTunnelAfterProxyV2Header() throws Exception {
        shouldMockRequestInConnectTunnelAfter(proxyV2Header(headerDestination.port()));
    }

    private void shouldMockRequestInConnectTunnelAfter(byte[] proxyHeader) throws Exception {
        try (Socket socket = connect()) {
            send(socket, proxyHeader, (connectRequest("127.0.0.1:" + PortFactory.findFreePort()) + "\r\n").getBytes(StandardCharsets.US_ASCII));
            assertThat(readHttpResponse(socket.getInputStream()), startsWith("HTTP/1.1 200"));

            send(socket, getRequest());

            String response = readHttpResponse(socket.getInputStream());
            assertThat(response, startsWith("HTTP/1.1 200"));
            assertThat(response.substring(response.indexOf("\r\n\r\n") + 4), startsWith(MOCKED_BODY));
        }
        assertThat(headerDestination.connections(), is(0));
    }

    @Test
    public void shouldForwardRequestInConnectTunnelToConnectTargetAfterProxyV1Header() throws Exception {
        shouldForwardRequestInConnectTunnelToConnectTargetAfter(proxyV1Header(headerDestination.port()));
    }

    @Test
    public void shouldForwardRequestInConnectTunnelToConnectTargetAfterProxyV2Header() throws Exception {
        shouldForwardRequestInConnectTunnelToConnectTargetAfter(proxyV2Header(headerDestination.port()));
    }

    private void shouldForwardRequestInConnectTunnelToConnectTargetAfter(byte[] proxyHeader) throws Exception {
        String target = "127.0.0.1:" + connectTarget.getLocalPort();
        try (Socket socket = connect()) {
            send(socket, proxyHeader, (connectRequest(target) + "\r\n").getBytes(StandardCharsets.US_ASCII));
            assertThat(readHttpResponse(socket.getInputStream()), startsWith("HTTP/1.1 200"));

            send(socket, ("GET " + FORWARDED_PATH + " HTTP/1.1\r\nHost: " + target + "\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.US_ASCII));

            String response = readHttpResponse(socket.getInputStream());
            assertThat(response, startsWith("HTTP/1.1 200"));
            assertThat(response.substring(response.indexOf("\r\n\r\n") + 4), is(FORWARDED_BODY));
        }
        assertThat(headerDestination.connections(), is(0));
    }

    private static String connectRequest(String target) {
        return "CONNECT " + target + " HTTP/1.1\r\nHost: " + target + "\r\n";
    }

    @Test
    public void shouldTerminateTlsAfterProxyV1Header() throws Exception {
        shouldTerminateTlsAfter(proxyV1Header(PortFactory.findFreePort()));
    }

    @Test
    public void shouldTerminateTlsAfterProxyV2Header() throws Exception {
        shouldTerminateTlsAfter(proxyV2Header(PortFactory.findFreePort()));
    }

    private void shouldTerminateTlsAfter(byte[] proxyHeader) throws Exception {
        try (Socket socket = connect()) {
            send(socket, proxyHeader);
            SSLSocket tls = (SSLSocket) new KeyStoreFactory(configuration(), new MockServerLogger()).sslContext().getSocketFactory()
                .createSocket(socket, "127.0.0.1", socket.getPort(), true);
            tls.setUseClientMode(true);
            tls.startHandshake();

            send(tls, getRequest());

            assertMockedFromProxySource(readHttpResponse(tls.getInputStream()));
        }
    }

    @Test
    public void shouldForwardBinaryAfterProxyV1Header() throws Exception {
        shouldForwardBinaryAfter(mockServer, proxyV1Header(binaryUpstream.getLocalPort()));
    }

    @Test
    public void shouldForwardBinaryAfterProxyV2Header() throws Exception {
        shouldForwardBinaryAfter(mockServer, proxyV2Header(binaryUpstream.getLocalPort()));
    }

    private void shouldForwardBinaryAfter(ClientAndServer server, byte[] proxyHeader) throws Exception {
        byte[] message = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        try (Socket socket = connect(server)) {
            send(socket, proxyHeader, message);

            assertThat(socket.getInputStream().readNBytes(message.length), is(message));
        }
        tryWaitForSuccess(() -> assertThat(binaryClientAddresses.toString(), binaryClientAddresses, contains((SocketAddress) new InetSocketAddress(SOURCE_IP, SOURCE_PORT))));
    }

    private static void assertMockedFromProxySource(String response) {
        assertThat(response, startsWith("HTTP/1.1 200"));
        assertThat(response.substring(response.indexOf("\r\n\r\n") + 4), is(MOCKED_BODY + SOURCE_IP + ":" + SOURCE_PORT));
    }

    private static byte[] proxyV1Header(int destinationPort) {
        return ("PROXY TCP4 " + SOURCE_IP + " 127.0.0.1 " + SOURCE_PORT + " " + destinationPort + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] proxyV2Header(int destinationPort) {
        ByteBuffer header = ByteBuffer.allocate(28);
        header.put(new byte[]{0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A});
        // version 2, PROXY command; IPv4 over TCP; a 12-byte address block
        header.put((byte) 0x21).put((byte) 0x11).putShort((short) 12);
        header.put(new byte[]{10, 9, 8, 7}).put(new byte[]{127, 0, 0, 1});
        header.putShort((short) SOURCE_PORT).putShort((short) destinationPort);
        return header.array();
    }

    private static byte[] getRequest() {
        return ("GET " + MOCKED_PATH + " HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] http2Frame(int type, int flags, int streamId, byte[] payload) {
        return ByteBuffer.allocate(9 + payload.length)
            .put((byte) (payload.length >> 16)).put((byte) (payload.length >> 8)).put((byte) payload.length)
            .put((byte) type).put((byte) flags).putInt(streamId).put(payload)
            .array();
    }

    /** A GET for the mocked path on stream 1, its header block HPACK-encoded without Huffman coding. */
    private static byte[] http2GetHeaders() {
        ByteArrayOutputStream block = new ByteArrayOutputStream();
        // :method GET, :scheme http, from the static table
        block.write(0x82);
        block.write(0x86);
        // :path and :authority, as literals with an indexed name
        writeLiteral(block, 0x04, MOCKED_PATH);
        writeLiteral(block, 0x01, "127.0.0.1");
        // END_STREAM and END_HEADERS
        return http2Frame(0x1, 0x5, 1, block.toByteArray());
    }

    private static void writeLiteral(ByteArrayOutputStream block, int nameIndex, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        block.write(nameIndex);
        block.write(bytes.length);
        block.write(bytes, 0, bytes.length);
    }

    /** Reads frames until stream 1 ends, and returns the body its DATA frames carried. */
    private static String readHttp2Body(InputStream input) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            byte[] head = input.readNBytes(9);
            if (head.length < 9) {
                throw new IOException("connection ended before the response did, body so far: " + body);
            }
            int length = ((head[0] & 0xFF) << 16) | ((head[1] & 0xFF) << 8) | (head[2] & 0xFF);
            int type = head[3];
            int flags = head[4];
            int streamId = ByteBuffer.wrap(head, 5, 4).getInt() & 0x7FFFFFFF;
            byte[] payload = input.readNBytes(length);
            if (streamId == 1 && type == 0x0) {
                body.write(payload, 0, payload.length);
            }
            if (streamId == 1 && (type == 0x0 || type == 0x1) && (flags & 0x1) != 0) {
                return body.toString(StandardCharsets.UTF_8);
            }
        }
    }

    private static Socket connect() throws IOException {
        return connect(mockServer);
    }

    private static Socket connect(ClientAndServer server) throws IOException {
        Socket socket = new Socket("127.0.0.1", server.getLocalPort());
        socket.setSoTimeout(10_000);
        socket.setTcpNoDelay(true);
        return socket;
    }

    private static void send(Socket socket, byte[]... parts) throws IOException {
        OutputStream output = socket.getOutputStream();
        for (byte[] part : parts) {
            output.write(part);
        }
        output.flush();
    }

    /** Reads one response: its head, then as many body bytes as its content-length says. */
    private static String readHttpResponse(InputStream input) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        while (!head.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
            int next = input.read();
            if (next < 0) {
                break;
            }
            head.write(next);
        }
        String headText = head.toString(StandardCharsets.US_ASCII);
        int contentLength = 0;
        for (String line : headText.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
            }
        }
        return headText + new String(input.readNBytes(contentLength), StandardCharsets.UTF_8);
    }

    private static void echoEachConnection() {
        while (!binaryUpstream.isClosed()) {
            try {
                Socket accepted = binaryUpstream.accept();
                Thread echo = new Thread(() -> {
                    try (Socket connection = accepted) {
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = connection.getInputStream().read(buffer)) >= 0) {
                            connection.getOutputStream().write(buffer, 0, read);
                            connection.getOutputStream().flush();
                        }
                    } catch (IOException ignored) {
                        // the client or the test closed the connection
                    }
                }, "proxy-protocol-binary-echo");
                echo.setDaemon(true);
                echo.start();
            } catch (IOException closed) {
                return;
            }
        }
    }

    /** Accepts connections and counts them, reading nothing. */
    private static final class CountingListener implements AutoCloseable {
        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private final AtomicInteger connections = new AtomicInteger();
        private final List<Socket> accepted = new CopyOnWriteArrayList<>();

        CountingListener() throws IOException {
            Thread acceptor = new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        accepted.add(serverSocket.accept());
                        connections.incrementAndGet();
                    } catch (IOException closed) {
                        // close() ends the accept
                    }
                }
            }, "proxy-protocol-header-destination");
            acceptor.setDaemon(true);
            acceptor.start();
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
            for (Socket socket : accepted) {
                socket.close();
            }
        }
    }
}
