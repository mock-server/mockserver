package org.mockserver.netty.integration.proxy.http;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.netty.MockServer;

import javax.net.ssl.SSLServerSocket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.testing.tls.SSLSocketFactory.sslSocketFactory;

/**
 * A binary (not HTTP) connection forwarded over TLS through an upstream proxy ({@code forwardHttpsProxy}) reads the
 * proxy's answer to {@code CONNECT} up to the {@code maxHeaderSize} of the server that forwards it, not the JVM-wide
 * setting. Two servers in this JVM, which relay each connection on one tunnelled upstream connection, are given a
 * limit below and a limit above the JVM-wide default of 262,144 bytes. Both forward to one TLS upstream on 127.0.0.1
 * through one proxy, whose {@code CONNECT} answer is the size each test sets.
 */
public class BinaryForwardConnectHeaderLimitIntegrationTest {

    private static final int DEFAULT_LIMIT = 256 * 1024;
    private static final int SMALL_LIMIT = 16 * 1024;
    private static final int LARGE_LIMIT = 512 * 1024;
    private static final String MESSAGE = "Hello not world!\n";

    private static TlsEchoUpstream upstream;
    private static ConnectProxy connectProxy;
    private static MockServer small;
    private static MockServerClient smallClient;
    private static MockServer large;
    private static MockServerClient largeClient;

    @BeforeClass
    public static void startServers() throws Exception {
        upstream = new TlsEchoUpstream();
        connectProxy = new ConnectProxy();
        small = new MockServer(throughTheProxy().maxHeaderSize(SMALL_LIMIT), upstream.port(), "127.0.0.1", 0);
        smallClient = new MockServerClient("127.0.0.1", small.getLocalPort());
        large = new MockServer(throughTheProxy().maxHeaderSize(LARGE_LIMIT), upstream.port(), "127.0.0.1", 0);
        largeClient = new MockServerClient("127.0.0.1", large.getLocalPort());
    }

    private static Configuration throughTheProxy() {
        // the test JVM defaults to ERROR, which would drop the WARN entries this class asserts on
        return configuration().logLevel("WARN").forwardHttpsProxy(new InetSocketAddress("127.0.0.1", connectProxy.port()));
    }

    @AfterClass
    public static void stopServers() throws Exception {
        stopQuietly(smallClient);
        stopQuietly(largeClient);
        stopQuietly(small);
        stopQuietly(large);
        if (connectProxy != null) {
            connectProxy.close();
        }
        if (upstream != null) {
            upstream.close();
        }
    }

    @Before
    public void forgetEarlierTests() {
        upstream.messages.clear();
        smallClient.reset();
        largeClient.reset();
    }

    @Test
    public void shouldLimitTheConnectResponseByAMaxHeaderSizeBelowTheDefault() throws Exception {
        for (int size : new int[]{SMALL_LIMIT - 1, SMALL_LIMIT}) {
            connectProxy.responseHeaderBytes = size;

            assertThat("a CONNECT answer of " + size + " bytes", send(small), is("echo:" + MESSAGE));
        }

        connectProxy.responseHeaderBytes = SMALL_LIMIT + 1;
        upstream.messages.clear();
        long started = System.nanoTime();

        assertThat("closed with no answer", send(small), is(""));

        assertThat("without waiting out the proxy connect timeout", TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started), lessThan(5L));
        assertRefusedOnce(smallClient, SMALL_LIMIT);
        assertThat("nothing was forwarded", upstream.messages, is(empty()));

        assertThat("the other server applies its own limit", send(large), is("echo:" + MESSAGE));
    }

    @Test
    public void shouldReadAConnectResponseOverTheDefaultWhenMaxHeaderSizeIsAboveIt() throws Exception {
        connectProxy.responseHeaderBytes = DEFAULT_LIMIT + 1;

        assertThat(send(large), is("echo:" + MESSAGE));

        connectProxy.responseHeaderBytes = LARGE_LIMIT + 1;
        upstream.messages.clear();

        assertThat("closed with no answer", send(large), is(""));

        assertRefusedOnce(largeClient, LARGE_LIMIT);
        assertThat("nothing was forwarded", upstream.messages, is(empty()));
    }

    /**
     * The servers log at WARN, so after the refused message's own record (received requests are recorded at any
     * level) the refusal is the only entry: the connection it closes is logged below WARN, and nothing logs it again.
     */
    private static void assertRefusedOnce(MockServerClient client, int limit) {
        List<String> logged = Arrays.asList(client.retrieveLogMessagesArray(null));
        int lastReceived = -1;
        for (int i = 0; i < logged.size(); i++) {
            if (logged.get(i).contains("received binary request")) {
                lastReceived = i;
            }
        }
        List<String> entries = logged.subList(lastReceived + 1, logged.size());
        assertThat(logged.toString(), entries.size(), is(1));
        assertThat(entries.get(0), containsString("failing forward"));
        assertThat(entries.get(0), containsString("the upstream proxy's CONNECT response headers are larger than maxHeaderSize (" + limit + " bytes)"));
    }

    /**
     * Sends {@link #MESSAGE} to {@code mockServer} over TLS and returns what comes back, which is nothing when
     * MockServer closes the connection instead. Every read, the TLS handshake's included, ends within 15 seconds.
     */
    private static String send(MockServer mockServer) throws Exception {
        try (Socket plain = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            plain.setSoTimeout(15_000);
            Socket socket = sslSocketFactory().wrapSocket(plain);
            socket.getOutputStream().write(MESSAGE.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            int expected = "echo:".length() + MESSAGE.length();
            InputStream input = socket.getInputStream();
            try {
                for (int read = input.read(); read != -1; read = input.read()) {
                    received.write(read);
                    if (received.size() == expected) {
                        break;
                    }
                }
            } catch (SocketTimeoutException noAnswer) {
                throw noAnswer;
            } catch (IOException closedWithoutCloseNotify) {
                // closed all the same
            } finally {
                socket.close();
            }
            return received.toString(StandardCharsets.UTF_8.name());
        }
    }

    /**
     * Answers each message it reads over TLS with {@code echo:} and the message.
     */
    private static final class TlsEchoUpstream {
        private final SSLServerSocket listener = sslSocketFactory().wrapSocket();
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        private final List<String> messages = new CopyOnWriteArrayList<>();

        private TlsEchoUpstream() throws IOException {
            daemon(() -> {
                while (!listener.isClosed()) {
                    Socket socket = listener.accept();
                    sockets.add(socket);
                    daemon(() -> echo(socket));
                }
            });
        }

        private int port() {
            return listener.getLocalPort();
        }

        private void echo(Socket socket) throws IOException {
            try {
                socket.setSoTimeout(30_000);
                byte[] buffer = new byte[1024];
                InputStream input = socket.getInputStream();
                for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
                    String message = new String(buffer, 0, read, StandardCharsets.UTF_8);
                    messages.add(message);
                    socket.getOutputStream().write(("echo:" + message).getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                }
            } finally {
                socket.close();
            }
        }

        private void close() throws IOException {
            listener.close();
            for (Socket socket : sockets) {
                socket.close();
            }
        }
    }

    /**
     * An HTTP proxy that answers {@code CONNECT} with {@code 200} and a header of the size asked for, as Netty's
     * HTTP/1.1 decoder counts it (the header line without its line end), then relays bytes between the two
     * connections.
     */
    private static final class ConnectProxy {
        private final ServerSocket listener = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        private final List<Socket> sockets = new CopyOnWriteArrayList<>();
        private volatile int responseHeaderBytes;

        private ConnectProxy() throws IOException {
            daemon(() -> {
                while (!listener.isClosed()) {
                    Socket client = listener.accept();
                    sockets.add(client);
                    daemon(() -> tunnel(client));
                }
            });
        }

        private int port() {
            return listener.getLocalPort();
        }

        private void tunnel(Socket client) throws IOException {
            client.setSoTimeout(30_000);
            StringBuilder head = new StringBuilder();
            InputStream fromClient = client.getInputStream();
            while (head.indexOf("\r\n\r\n") < 0) {
                int read = fromClient.read();
                if (read == -1) {
                    return;
                }
                head.append((char) read);
            }
            String[] target = head.substring("CONNECT ".length(), head.indexOf(" HTTP/1.1")).split(":");
            Socket upstream = new Socket("127.0.0.1", Integer.parseInt(target[1]));
            sockets.add(upstream);
            // "x-proxy: " is 9 bytes
            String response = "HTTP/1.1 200 Connection established\r\nx-proxy: " + "a".repeat(responseHeaderBytes - 9) + "\r\n\r\n";
            client.getOutputStream().write(response.getBytes(StandardCharsets.ISO_8859_1));
            client.getOutputStream().flush();
            daemon(() -> copy(upstream, client));
            copy(client, upstream);
        }

        private static void copy(Socket from, Socket to) throws IOException {
            try {
                byte[] buffer = new byte[16 * 1024];
                InputStream input = from.getInputStream();
                OutputStream output = to.getOutputStream();
                for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
                    output.write(buffer, 0, read);
                    output.flush();
                }
            } finally {
                from.close();
                to.close();
            }
        }

        private void close() throws IOException {
            listener.close();
            for (Socket socket : sockets) {
                socket.close();
            }
        }
    }

    private static void daemon(IoTask task) {
        Thread thread = new Thread(() -> {
            try {
                task.run();
            } catch (IOException closed) {
                // the listener or a connection was closed
            }
        }, "binary-forward-fixture");
        thread.setDaemon(true);
        thread.start();
    }

    private interface IoTask {
        void run() throws IOException;
    }
}
