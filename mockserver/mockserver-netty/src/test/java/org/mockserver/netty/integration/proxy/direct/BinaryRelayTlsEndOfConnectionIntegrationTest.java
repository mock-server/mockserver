package org.mockserver.netty.integration.proxy.direct;

import org.junit.After;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.MockServer;
import org.mockserver.socket.tls.KeyStoreFactory;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.netty.integration.proxy.direct.StartTlsUpstream.SSL_REQUEST;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * When the client of a binary connection relayed on one upstream connection that MockServer upgraded to TLS leaves,
 * the upstream session is ended with a {@code close_notify}, and the connection is half-closed as a clear one is:
 * what the upstream still sends is read, not answered with a reset, until the upstream closes or the linger limit.
 */
public class BinaryRelayTlsEndOfConnectionIntegrationTest {

    private static final byte[] MESSAGE = {'Q', 0, 0, 0, 13, 'S', 'E', 'L', 'E', 'C', 'T', ' ', '1', 0};
    private static final int CHUNK_BYTES = 16 * 1024;
    private static final int CHUNKS = 30;

    private MockServer mockServer;
    private ServerSocket upstream;
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();
    private final CompletableFuture<ObservedSocket> upstreamRaw = new CompletableFuture<>();

    @After
    public void stop() throws IOException {
        stopQuietly(mockServer);
        for (Socket socket : sockets) {
            socket.close();
        }
        if (upstream != null) {
            upstream.close();
        }
    }

    @Test
    public void shouldReadWhatTheUpstreamSendsAfterTheCloseNotifyOverTls13() throws Exception {
        shouldReadWhatTheUpstreamSendsAfterTheCloseNotify("TLSv1.3");
    }

    @Test
    public void shouldReadWhatTheUpstreamSendsAfterTheCloseNotifyOverTls12() throws Exception {
        shouldReadWhatTheUpstreamSendsAfterTheCloseNotify("TLSv1.2");
    }

    private void shouldReadWhatTheUpstreamSendsAfterTheCloseNotify(String protocol) throws Exception {
        CompletableFuture<SSLSocket> upstreamSession = start(protocol);
        Socket client = new Socket();
        sockets.add(client);
        client.setSoTimeout(10_000);
        client.connect(new InetSocketAddress("127.0.0.1", mockServer.getLocalPort()), 10_000);
        client.getOutputStream().write(SSL_REQUEST);
        client.getOutputStream().flush();
        assertThat(client.getInputStream().read(), is((int) 'S'));
        SSLSocket clientTls = StartTlsUpstream.startTlsAsClient(client, "127.0.0.1", protocol, false);
        sockets.add(clientTls);
        clientTls.getOutputStream().write(MESSAGE);
        clientTls.getOutputStream().flush();

        SSLSocket upstreamEnd = upstreamSession.get(10, TimeUnit.SECONDS);
        upstreamEnd.setSoTimeout(10_000);
        InputStream input = upstreamEnd.getInputStream();
        assertThat(Arrays.equals(input.readNBytes(MESSAGE.length), MESSAGE), is(true));

        clientTls.close();
        // MockServer ends its output only once its close_notify has been written
        assertThat("MockServer ended its output", upstreamRaw.get(10, TimeUnit.SECONDS).outputOfPeerEnded.await(10, TimeUnit.SECONDS), is(true));

        // the upstream's reply to what the client sent goes on after the close_notify, which it has not read yet
        byte[] chunk = new byte[CHUNK_BYTES];
        Arrays.fill(chunk, (byte) 'r');
        OutputStream output = upstreamEnd.getOutputStream();
        int written = 0;
        String failure = "none";
        try {
            for (; written < CHUNKS; written++) {
                output.write(chunk);
                output.flush();
                Thread.sleep(20);
            }
        } catch (IOException e) {
            failure = e.toString();
        }
        assertThat("chunks the upstream sent after MockServer's close_notify (failure: " + failure + ")", written, is(CHUNKS));

        assertThat("then the upstream reads the close_notify", input.read(), is(-1));
    }

    private CompletableFuture<SSLSocket> start(String protocol) throws IOException {
        upstream = new ServerSocket() {
            @Override
            public Socket accept() throws IOException {
                ObservedSocket socket = new ObservedSocket();
                implAccept(socket);
                upstreamRaw.complete(socket);
                return socket;
            }
        };
        upstream.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 0));
        mockServer = new MockServer(
            configuration()
                .logLevel("WARN")
                .startupWarmup(false)
                .proxySetup(false),
            upstream.getLocalPort(), "127.0.0.1", 0
        );
        CompletableFuture<SSLSocket> upstreamSession = new CompletableFuture<>();
        Thread accept = new Thread(() -> {
            try {
                Socket socket = upstream.accept();
                sockets.add(socket);
                socket.setSoTimeout(10_000);
                byte[] sslRequest = socket.getInputStream().readNBytes(SSL_REQUEST.length);
                if (!Arrays.equals(sslRequest, SSL_REQUEST)) {
                    throw new IOException("expected an SSLRequest but got " + Arrays.toString(sslRequest));
                }
                socket.getOutputStream().write('S');
                socket.getOutputStream().flush();
                SSLSocket tls = (SSLSocket) new KeyStoreFactory(configuration(), new MockServerLogger()).sslContext().getSocketFactory().createSocket(socket, null, true);
                sockets.add(tls);
                tls.setUseClientMode(false);
                tls.setEnabledProtocols(new String[]{protocol});
                tls.startHandshake();
                upstreamSession.complete(tls);
            } catch (Exception e) {
                upstreamSession.completeExceptionally(e);
            }
        }, "binary-relay-tls-upstream");
        accept.setDaemon(true);
        accept.start();
        return upstreamSession;
    }
    /**
     * The upstream's TCP connection, read ahead of its TLS layer: it sees MockServer end its output (the FIN after
     * the close_notify) while the TLS layer above has not yet read the close_notify.
     */
    private static class ObservedSocket extends Socket {

        private static final byte[] END = new byte[0];
        final CountDownLatch outputOfPeerEnded = new CountDownLatch(1);
        private final BlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
        private volatile IOException failure;
        private InputStream readAhead;

        @Override
        public synchronized InputStream getInputStream() throws IOException {
            if (readAhead == null) {
                InputStream raw = super.getInputStream();
                Thread pump = new Thread(() -> pump(raw), "binary-relay-tls-upstream-read-ahead");
                pump.setDaemon(true);
                pump.start();
                readAhead = new QueuedInput();
            }
            return readAhead;
        }

        private void pump(InputStream raw) {
            byte[] buffer = new byte[16 * 1024];
            try {
                for (int read; (read = raw.read(buffer)) != -1; ) {
                    received.add(Arrays.copyOf(buffer, read));
                }
                outputOfPeerEnded.countDown();
            } catch (IOException e) {
                failure = e;
            }
            received.add(END);
        }

        private class QueuedInput extends InputStream {
            private byte[] current = new byte[0];
            private int position;

            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                if (length == 0) {
                    return 0;
                }
                while (current != END && position == current.length) {
                    try {
                        byte[] next = received.poll(getSoTimeout() > 0 ? getSoTimeout() : Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                        if (next == null) {
                            throw new SocketTimeoutException("nothing read within " + getSoTimeout() + " ms");
                        }
                        current = next;
                        position = 0;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException();
                    }
                }
                if (current == END) {
                    received.add(END);
                    if (failure != null) {
                        throw failure;
                    }
                    return -1;
                }
                int count = Math.min(length, current.length - position);
                System.arraycopy(current, position, bytes, offset, count);
                position += count;
                return count;
            }
        }
    }
}
