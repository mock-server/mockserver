package org.mockserver.netty.integration.proxy.direct;

import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.netty.MockServer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.Retries.tryWaitForSuccess;
import static org.mockserver.testing.tls.SSLSocketFactory.sslSocketFactory;

/**
 * What a connection is taken to be from its first bytes, over real sockets: a short binary message is forwarded
 * without waiting for more, a binary connection stays binary, and a client whose first bytes arrive one at a
 * time is still taken for what it is.
 */
public class ProtocolDetectionIntegrationTest {

    private MockServer mockServer;

    @After
    public void stopMockServer() {
        stopQuietly(mockServer);
    }

    private Socket clientOfMockServerForwardingTo(Upstream upstream) throws IOException {
        mockServer = new MockServer(Configuration.configuration(), upstream.port(), "127.0.0.1", 0);
        Socket client = new Socket("127.0.0.1", mockServer.getLocalPort());
        client.setTcpNoDelay(true);
        return client;
    }

    private static void send(Socket socket, String message) throws IOException {
        socket.getOutputStream().write(message.getBytes(StandardCharsets.ISO_8859_1));
        socket.getOutputStream().flush();
    }

    /** The messages, in order, on the client's one upstream connection. */
    private static void awaitReceived(Upstream upstream, String... messages) {
        tryWaitForSuccess(() -> assertThat(upstream.received(), contains(String.join("", messages))));
    }

    @Test
    public void shouldForwardAShortFirstBinaryMessage() throws Exception {
        try (Upstream upstream = new Upstream(); Socket client = clientOfMockServerForwardingTo(upstream)) {
            send(client, "hello\n");

            awaitReceived(upstream, "hello\n");
        }
    }

    @Test
    public void shouldForwardShortLaterBinaryMessages() throws Exception {
        try (Upstream upstream = new Upstream(); Socket client = clientOfMockServerForwardingTo(upstream)) {
            send(client, "a first message\n");
            awaitReceived(upstream, "a first message\n");

            send(client, "short\n");
            awaitReceived(upstream, "a first message\n", "short\n");

            send(client, "x");
            awaitReceived(upstream, "a first message\n", "short\n", "x");
        }
    }

    @Test
    public void shouldForwardALaterBinaryMessageThatStartsLikeAnotherProtocolAsItIs() throws Exception {
        String likeHttp = "GET /some/path HTTP/1.1\r\nHost: example.com\r\n\r\n";
        String likeATlsRecord = "\u0016\u0003\u0001\u0000\u0005hello";
        try (Upstream upstream = new Upstream(); Socket client = clientOfMockServerForwardingTo(upstream)) {
            send(client, "a first message\n");
            awaitReceived(upstream, "a first message\n");

            send(client, likeATlsRecord);
            awaitReceived(upstream, "a first message\n", likeATlsRecord);

            send(client, likeHttp);
            awaitReceived(upstream, "a first message\n", likeATlsRecord, likeHttp);

            send(client, "POST");
            awaitReceived(upstream, "a first message\n", likeATlsRecord, likeHttp, "POST");
        }
    }

    @Test
    public void shouldForwardAFirstMessageThatIsOnlyTheStartOfAKnownProtocolOnceNothingMoreArrives() throws Exception {
        try (Upstream upstream = new Upstream(); Socket client = clientOfMockServerForwardingTo(upstream)) {
            long sent = System.nanoTime();
            send(client, "GET");

            awaitReceived(upstream, "GET");
            // the wait is one second; a loaded machine can only make this longer
            assertThat("held while the rest of a request line could still arrive", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sent), greaterThanOrEqualTo(900L));
        }
    }

    @Test
    public void shouldForwardAHeldFirstMessageWhenTheClientCloses() throws Exception {
        try (Upstream upstream = new Upstream()) {
            try (Socket client = clientOfMockServerForwardingTo(upstream)) {
                send(client, "GET");
            }

            awaitReceived(upstream, "GET");
        }
    }

    @Test
    public void shouldAnswerAnHttpRequestWhoseFirstBytesArriveOneAtATime() throws Exception {
        mockServer = new MockServer(0);

        assertThat(statusLineOfARequestWhoseFirstBytesAreSentOneAtATime(false), startsWith("HTTP/1.1 404"));
    }

    @Test
    public void shouldAnswerAClientWhoseTlsHandshakeStartsOneByteAtATime() throws Exception {
        mockServer = new MockServer(0);

        assertThat(statusLineOfARequestWhoseFirstBytesAreSentOneAtATime(true), startsWith("HTTP/1.1 404"));
    }

    /**
     * An attempt in which any two of the first bytes were written more than half a second apart shows nothing
     * (see {@link OneByteAtATimeSocket}) and is made again; in one that counts, each byte reached MockServer
     * well before it could have given up on the one before.
     */
    private String statusLineOfARequestWhoseFirstBytesAreSentOneAtATime(boolean overTls) throws IOException {
        int attempts = 5;
        for (int attempt = 0; attempt < attempts; attempt++) {
            OneByteAtATimeSocket socket = new OneByteAtATimeSocket("127.0.0.1", mockServer.getLocalPort());
            String statusLine;
            try {
                socket.setSoTimeout(10_000);
                Socket client = overTls ? sslSocketFactory().wrapSocket(socket) : socket;
                send(client, requestForNothingMocked());
                statusLine = statusLine(client);
            } catch (IOException failed) {
                statusLine = "failed: " + failed;
            } finally {
                socket.close();
            }
            if (socket.longestGapMillis() < 500) {
                return statusLine;
            }
        }
        throw new AssertionError("the first bytes could not be written less than 500 ms apart in " + attempts + " attempts");
    }

    /** Addressed to MockServer itself, so it is answered here, with a 404, and not forwarded. */
    private String requestForNothingMocked() {
        return "GET /not/mocked HTTP/1.1\r\nHost: 127.0.0.1:" + mockServer.getLocalPort() + "\r\nConnection: close\r\n\r\n";
    }

    private static String statusLine(Socket client) throws IOException {
        String line = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1)).readLine();
        return line == null ? "connection closed without a response" : line;
    }

    /**
     * An upstream that never answers, and records what each connection received, in the order the connections were
     * accepted.
     */
    private static class Upstream implements AutoCloseable {

        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        private final List<Socket> connections = new CopyOnWriteArrayList<>();
        private final List<ByteArrayOutputStream> receivedByConnection = new CopyOnWriteArrayList<>();

        Upstream() throws IOException {
            Thread accept = new Thread(this::acceptConnections, "upstream-accept");
            accept.setDaemon(true);
            accept.start();
        }

        private void acceptConnections() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket connection = serverSocket.accept();
                    ByteArrayOutputStream received = new ByteArrayOutputStream();
                    connections.add(connection);
                    receivedByConnection.add(received);
                    if (serverSocket.isClosed()) {
                        connection.close();
                    }
                    Thread read = new Thread(() -> readUntilClosed(connection, received), "upstream-read");
                    read.setDaemon(true);
                    read.start();
                } catch (IOException closed) {
                    // close() ends the accept
                }
            }
        }

        private static void readUntilClosed(Socket connection, ByteArrayOutputStream received) {
            byte[] buffer = new byte[10000];
            try {
                InputStream inputStream = connection.getInputStream();
                for (int read; (read = inputStream.read(buffer)) != -1; ) {
                    synchronized (received) {
                        received.write(buffer, 0, read);
                    }
                }
            } catch (IOException closed) {
                // MockServer, or close(), ended the connection
            }
        }

        List<String> received() {
            List<String> messages = new ArrayList<>();
            for (ByteArrayOutputStream received : receivedByConnection) {
                synchronized (received) {
                    messages.add(new String(received.toByteArray(), StandardCharsets.ISO_8859_1));
                }
            }
            return messages;
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            for (Socket connection : connections) {
                connection.close();
            }
        }
    }
}
