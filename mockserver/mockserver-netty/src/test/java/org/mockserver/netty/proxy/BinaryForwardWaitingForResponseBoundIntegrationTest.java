package org.mockserver.netty.proxy;

import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.model.BinaryProxyListener;
import org.mockserver.netty.MockServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.netty.proxy.BinaryForwardUpstream.await;
import static org.mockserver.netty.proxy.BinaryForwardUpstream.awaitNoLongerGrowing;
import static org.mockserver.netty.proxy.BinaryRequestProxyingHandler.MAX_UNANSWERED_FORWARDS;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Floods MockServer, forwarding binary messages one per upstream connection and waiting for each response. What
 * MockServer keeps for messages already forwarded stays bounded: the client is held back rather than given an
 * upstream connection per message, and an upstream that never answers has its connections, and the client's, closed
 * after maxSocketTimeout.
 */
public class BinaryForwardWaitingForResponseBoundIntegrationTest {

    private static final long MAX_SOCKET_TIMEOUT_MILLIS = 5_000;
    /** Messages read by MockServer after it stops reading the client: what the read under way still held. */
    private static final int OVERSHOOT = 1;
    private static final long TO_SEND = 16L * 1024 * 1024;

    private BinaryForwardUpstream upstream;
    private MockServer mockServer;
    private final AtomicLong bytesWritten = new AtomicLong();
    private final AtomicReference<Throwable> writeFailure = new AtomicReference<>();
    private final AtomicBoolean stopWriting = new AtomicBoolean();

    @After
    public void stop() throws InterruptedException {
        stopWriting.set(true);
        stopQuietly(mockServer);
        if (upstream != null) {
            upstream.close();
        }
    }

    private void mockServerForwardingTo(BinaryForwardUpstream upstream, BinaryProxyListener listener) {
        this.upstream = upstream;
        Configuration configuration = Configuration.configuration()
            .forwardBinaryRequestsUseSingleConnection(false)
            .forwardBinaryRequestsWithoutWaitingForResponse(false)
            .maxSocketTimeoutInMillis(MAX_SOCKET_TIMEOUT_MILLIS)
            // each message is logged as it is read; the bytes logged with it are not what is tested
            .maxLoggedBodyBytes(64)
            .binaryProxyListener(listener);
        mockServer = new MockServer(configuration, upstream.port(), "127.0.0.1", 0);
    }

    /** Writes as fast as the client is read, until {@link #TO_SEND} bytes are written or the connection fails. */
    private Thread floodingClient(Socket client) {
        Thread writer = new Thread(() -> {
            try {
                OutputStream output = client.getOutputStream();
                byte[] chunk = new byte[1024];
                Arrays.fill(chunk, (byte) 'x');
                chunk[0] = 'H';
                while (bytesWritten.get() < TO_SEND && !stopWriting.get()) {
                    output.write(chunk);
                    output.flush();
                    bytesWritten.addAndGet(chunk.length);
                }
            } catch (Throwable throwable) {
                writeFailure.set(throwable);
            }
        }, "flooding-client");
        writer.setDaemon(true);
        writer.start();
        return writer;
    }

    /** @return true once the stream ends or is reset, false if it delivers a byte or nothing before the read times out */
    private static boolean closed(InputStream input) {
        try {
            return input.read() < 0;
        } catch (SocketTimeoutException stillOpen) {
            return false;
        } catch (Exception resetByPeer) {
            return true;
        }
    }

    @Test
    public void shouldHoldBackAFloodingClientAndCloseItAfterMaxSocketTimeoutWhenTheUpstreamNeverAnswers() throws Exception {
        mockServerForwardingTo(BinaryForwardUpstream.silent(), null);

        try (Socket client = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            long connected = System.nanoTime();
            client.setSendBufferSize(64 * 1024);
            Thread writer = floodingClient(client);

            long writtenWhenHeldBack = awaitNoLongerGrowing(bytesWritten, TO_SEND);
            assertThat("the client can no longer write: it is held back", writtenWhenHeldBack, lessThan(TO_SEND));
            assertThat("rather than given an upstream connection for each message", upstream.accepted.get(), lessThanOrEqualTo(MAX_UNANSWERED_FORWARDS + OVERSHOOT));

            client.setSoTimeout((int) (MAX_SOCKET_TIMEOUT_MILLIS * 12));
            assertThat("MockServer closes the client connection once a forward has had no response for maxSocketTimeout", closed(client.getInputStream()), is(true));
            long closedAfterMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connected);
            assertThat("and not before", closedAfterMillis, greaterThanOrEqualTo(MAX_SOCKET_TIMEOUT_MILLIS));

            writer.join(TimeUnit.SECONDS.toMillis(60));
            assertThat("the client's writes fail, as the connection is closed", writer.isAlive(), is(false));
            assertThat(writeFailure.get(), is(notNullValue()));
        }

        await("every upstream connection is closed too", () -> upstream.open.get() == 0);
        assertThat("and none was ever opened beyond the limit", upstream.mostOpen.get(), lessThanOrEqualTo(MAX_UNANSWERED_FORWARDS + OVERSHOOT));
        assertThat(upstream.accepted.get(), lessThanOrEqualTo(MAX_UNANSWERED_FORWARDS + OVERSHOOT));
    }

    /**
     * The listener is called for each response, on the scheduler's threads, before the response is written back; one
     * that does not return holds the client back too, for longer than maxSocketTimeout without failing a forward that
     * has been answered.
     */
    @Test
    public void shouldHoldBackAFloodingClientWhileTheListenerIsBehindLosingNothing() throws Exception {
        byte[] reply = "ok\n".getBytes(StandardCharsets.UTF_8);
        CountDownLatch listenerMayReturn = new CountDownLatch(1);
        AtomicLong bytesReported = new AtomicLong();
        mockServerForwardingTo(BinaryForwardUpstream.answeringEachConnectionWith(reply), (binaryRequest, binaryResponse, serverAddress, clientAddress) -> {
            try {
                listenerMayReturn.await(120, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                bytesReported.addAndGet(binaryRequest.getBytes().length);
            }
        });
        AtomicLong repliesRead = new AtomicLong();

        try (Socket client = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            client.setSendBufferSize(64 * 1024);
            Thread reader = new Thread(() -> {
                try {
                    InputStream input = client.getInputStream();
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        repliesRead.addAndGet(read);
                    }
                } catch (Exception closed) {
                    // the test is over
                }
            }, "client-reader");
            reader.setDaemon(true);
            reader.start();
            Thread writer = floodingClient(client);
            try {
                long writtenWhenHeldBack = awaitNoLongerGrowing(bytesWritten, TO_SEND);
                assertThat("the client can no longer write: it is held back", writtenWhenHeldBack, lessThan(TO_SEND));
                assertThat("rather than forwarded without limit", upstream.accepted.get(), lessThanOrEqualTo(MAX_UNANSWERED_FORWARDS + OVERSHOOT));
                assertThat("the listener has returned for none of them", bytesReported.get(), is(0L));

                // past maxSocketTimeout, which must not fail a forward that was answered
                TimeUnit.MILLISECONDS.sleep(MAX_SOCKET_TIMEOUT_MILLIS * 3 / 2);
                listenerMayReturn.countDown();

                writer.join(TimeUnit.SECONDS.toMillis(120));
                assertThat("the client finishes once the listener catches up", writer.isAlive(), is(false));
                assertThat(writeFailure.get(), is(nullValue()));
                await("everything reaches the upstream", () -> upstream.bytesReceived.get() == TO_SEND);
                await("and is reported to the listener", () -> bytesReported.get() == TO_SEND);
                await("and every forward's reply reaches the client", () -> repliesRead.get() == (long) upstream.accepted.get() * reply.length);
                assertThat("whose connection stays open", reader.isAlive(), is(true));
            } finally {
                stopWriting.set(true);
                listenerMayReturn.countDown();
            }
        }
    }
}
