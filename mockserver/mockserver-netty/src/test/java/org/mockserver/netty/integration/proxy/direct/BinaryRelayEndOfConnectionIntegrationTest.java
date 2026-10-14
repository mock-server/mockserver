package org.mockserver.netty.integration.proxy.direct;

import org.junit.After;
import org.junit.Test;
import org.mockserver.netty.MockServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * When the client of a binary connection relayed on one upstream connection leaves, the upstream connection's output is
 * ended once what the client sent has been written to it, and the connection closes when the upstream closes it or at
 * the linger limit (5 s), whether or not the upstream has taken everything.
 * <p>
 * The upstream reads from a small receive buffer, slowly or not at all, so the relay holds bytes for it that cannot
 * yet be sent, and stops reading the client.
 */
public class BinaryRelayEndOfConnectionIntegrationTest {

    private static final int SMALL_BUFFER = 16 * 1024;
    private static final int CHUNK_BYTES = 16 * 1024;
    private static final int SENT_BEFORE_LEAVING = 128 * CHUNK_BYTES;

    private MockServer mockServer;
    private ServerSocket upstream;
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();
    private final AtomicLong flooded = new AtomicLong();

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
    public void shouldCloseTheUpstreamConnectionWithinTheLingerLimitWhenTheClientLeavesAndTheUpstreamNeverReads() throws Exception {
        CompletableFuture<Socket> upstreamSocket = start();
        Socket client = connectedClient();
        daemon(() -> send(client, Long.MAX_VALUE));
        Socket upstreamEnd = upstreamSocket.get(10, TimeUnit.SECONDS);
        awaitStalled();
        client.setSoLinger(true, 0);
        client.close();

        // the upstream goes on sending, which shows the relay that the client has gone (what it relays to the client
        // fails to be written) and then whether it still holds the upstream's connection open; it never reads what is
        // queued for it
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        boolean closed = false;
        while (!closed && System.nanoTime() < deadline) {
            try {
                upstreamEnd.getOutputStream().write('u');
                upstreamEnd.getOutputStream().flush();
                Thread.sleep(20);
            } catch (IOException e) {
                closed = true;
            }
        }
        assertThat("the upstream's connection was closed within 20 seconds", closed, is(true));
    }

    @Test
    public void shouldDeliverEverythingTheClientSentThenEndTheOutputOfASlowUpstreamsConnection() throws Exception {
        CompletableFuture<Socket> upstreamSocket = start();
        Socket client = connectedClient();
        daemon(() -> {
            send(client, SENT_BEFORE_LEAVING);
            try {
                client.close();
            } catch (IOException e) {
                // the test fails on what the upstream received
            }
        });
        Socket upstreamEnd = upstreamSocket.get(10, TimeUnit.SECONDS);
        upstreamEnd.setSoTimeout(20_000);

        InputStream input = upstreamEnd.getInputStream();
        byte[] buffer = new byte[4 * 1024];
        long received = 0;
        for (int read; (read = input.read(buffer)) != -1; ) {
            received += read;
            Thread.sleep(1);
        }
        assertThat("everything the client sent before leaving, followed by the end of the stream", received, is((long) SENT_BEFORE_LEAVING));

        // only MockServer's output has ended: what the upstream still sends is read, not answered with a reset
        OutputStream output = upstreamEnd.getOutputStream();
        output.write('u');
        output.flush();
        Thread.sleep(200);
        output.write('u');
        output.flush();
    }

    private CompletableFuture<Socket> start() throws IOException {
        upstream = new ServerSocket();
        upstream.setReceiveBufferSize(SMALL_BUFFER);
        upstream.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 0));
        mockServer = new MockServer(
            configuration()
                .logLevel("WARN")
                .startupWarmup(false)
                .proxySetup(false)
                // far beyond the tests' deadlines, so that only the client leaving can close the upstream connection
                .responseWriteStallTimeoutMillis(TimeUnit.MINUTES.toMillis(5)),
            upstream.getLocalPort(), "127.0.0.1", 0
        );
        CompletableFuture<Socket> upstreamSocket = new CompletableFuture<>();
        daemon(() -> {
            try {
                Socket socket = upstream.accept();
                sockets.add(socket);
                // set on the accepted socket too, so that its receive window is not opened again by the kernel
                socket.setReceiveBufferSize(SMALL_BUFFER);
                upstreamSocket.complete(socket);
            } catch (IOException e) {
                upstreamSocket.completeExceptionally(e);
            }
        });
        return upstreamSocket;
    }

    private Socket connectedClient() throws IOException {
        Socket client = new Socket();
        sockets.add(client);
        client.setSendBufferSize(SMALL_BUFFER);
        client.connect(new InetSocketAddress("127.0.0.1", mockServer.getLocalPort()), 10_000);
        return client;
    }

    private void send(Socket socket, long bytes) {
        byte[] chunk = new byte[CHUNK_BYTES];
        Arrays.fill(chunk, (byte) 'x');
        try {
            OutputStream output = socket.getOutputStream();
            while (flooded.get() < bytes) {
                output.write(chunk);
                flooded.addAndGet(chunk.length);
            }
        } catch (IOException e) {
            // the client has left
        }
    }

    /**
     * Waits until the client's writes have stopped going through: MockServer is holding bytes for the upstream and has
     * stopped reading the client.
     */
    private void awaitStalled() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        long last = -1;
        long stableSince = System.nanoTime();
        while (System.nanoTime() < deadline) {
            long now = flooded.get();
            if (now != last) {
                last = now;
                stableSince = System.nanoTime();
            } else if (now > 0 && System.nanoTime() - stableSince > TimeUnit.MILLISECONDS.toNanos(1_000)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("the client's writes never stopped going through, " + flooded.get() + " bytes written");
    }

    private static void daemon(Runnable runnable) {
        Thread thread = new Thread(runnable, "binary-relay-end-of-connection");
        thread.setDaemon(true);
        thread.start();
    }
}
