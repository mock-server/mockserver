package org.mockserver.netty.proxy;

import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.model.BinaryProxyListener;
import org.mockserver.netty.MockServer;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.netty.proxy.BinaryForwardUpstream.await;
import static org.mockserver.netty.proxy.BinaryForwardUpstream.awaitNoLongerGrowing;
import static org.mockserver.netty.proxy.BinaryForwardUpstream.readsNothing;
import static org.mockserver.netty.proxy.BinaryRequestProxyingHandler.MAX_PENDING_LISTENER_CALLS;
import static org.mockserver.netty.proxy.BinaryRequestProxyingHandler.MAX_UNANSWERED_FORWARDS;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Floods MockServer, forwarding binary messages one per upstream connection without waiting for responses, against an
 * upstream that accepts every connection, reads everything and never answers or closes. What MockServer keeps for
 * messages already forwarded stays bounded: the upstream connections left open, the messages not yet reported to the
 * listener, and the client is held back rather than its messages held in memory.
 */
public class BinaryForwardWithoutWaitingBacklogIntegrationTest {

    private static final long MAX_SOCKET_TIMEOUT_MILLIS = 5_000;
    /** A connection closed by MockServer is seen closed by the upstream a little later. */
    private static final int CLOSE_LAG = 8;

    private final BinaryForwardUpstream upstream = BinaryForwardUpstream.silent();
    private final AtomicInteger listenerCalls = new AtomicInteger();
    private final AtomicLong bytesReported = new AtomicLong();
    private final AtomicInteger responsesReported = new AtomicInteger();
    private final AtomicInteger mostListenerCallsBehind = new AtomicInteger();
    private MockServer mockServer;

    @After
    public void stop() throws InterruptedException {
        stopQuietly(mockServer);
        upstream.close();
    }

    private void mockServerForwardingTo(BinaryProxyListener listener) {
        Configuration configuration = Configuration.configuration()
            .forwardBinaryRequestsUseSingleConnection(false)
            .forwardBinaryRequestsWithoutWaitingForResponse(true)
            .maxSocketTimeoutInMillis(MAX_SOCKET_TIMEOUT_MILLIS)
            .binaryProxyListener(listener);
        mockServer = new MockServer(configuration, upstream.port(), "127.0.0.1", 0);
    }

    /** Messages forwarded (one upstream connection each) that the listener has not yet been told of and returned from. */
    private void listenerReturned(int messageLength) {
        bytesReported.addAndGet(messageLength);
        int returned = listenerCalls.incrementAndGet();
        mostListenerCallsBehind.accumulateAndGet(upstream.accepted.get() - returned, Math::max);
    }

    /**
     * A listener that waits for each message's response, which this upstream never sends: each wait ends as its
     * forward is given up, so the client is never held back waiting for maxSocketTimeout.
     */
    @Test
    public void shouldBoundConnectionsAndListenerCallsForAListenerWaitingOnResponsesThatNeverCome() throws Exception {
        mockServerForwardingTo((binaryRequest, binaryResponse, serverAddress, clientAddress) -> {
            try {
                if (binaryResponse.get(60, TimeUnit.SECONDS) == null) {
                    responsesReported.incrementAndGet();
                }
            } catch (Exception notAnswered) {
                // counted as not reported
            } finally {
                listenerReturned(binaryRequest.getBytes().length);
            }
        });
        int messages = MAX_UNANSWERED_FORWARDS * 6;
        long bytesSent = 0;

        try (Socket client = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            client.setTcpNoDelay(true);
            OutputStream output = client.getOutputStream();
            for (int i = 0; i < messages; i++) {
                byte[] message = ("Hello upstream, message " + i + "\n").getBytes(StandardCharsets.UTF_8);
                output.write(message);
                output.flush();
                bytesSent += message.length;
                // one message at a time, so each is read, and forwarded, on its own
                long expected = bytesSent;
                await("message " + i + " reaches the upstream well within maxSocketTimeout", MAX_SOCKET_TIMEOUT_MILLIS * 3 / 4, () -> upstream.bytesReceived.get() >= expected);
            }

            assertThat("every message was forwarded on a connection of its own", upstream.accepted.get(), is(messages));
            assertThat("nothing was lost", upstream.bytesReceived.get(), is(bytesSent));
            assertThat("the upstream never had more than the limit open at once", upstream.mostOpen.get(), lessThanOrEqualTo(MAX_UNANSWERED_FORWARDS + CLOSE_LAG));
            assertThat("the listener was never further behind than the limit", mostListenerCallsBehind.get(), lessThanOrEqualTo(MAX_PENDING_LISTENER_CALLS));
            await("the listener catches up with all but those still waiting", () -> listenerCalls.get() >= messages - MAX_UNANSWERED_FORWARDS);

            client.setSoTimeout(200);
            assertThat("MockServer neither answered nor closed the client connection", readsNothing(client.getInputStream()), is(true));
        }

        await("the forwards the client left unanswered are given up after maxSocketTimeout", () -> upstream.open.get() == 0);
        await("and the listener is told of each", () -> listenerCalls.get() == messages);
        assertThat("with no response", responsesReported.get(), is(messages));
        assertThat(bytesReported.get(), is(bytesSent));
    }

    @Test
    public void shouldHoldBackAFloodingClientWhileTheListenerIsBehind() throws Exception {
        CountDownLatch listenerMayReturn = new CountDownLatch(1);
        AtomicInteger largestMessage = new AtomicInteger();
        mockServerForwardingTo((binaryRequest, binaryResponse, serverAddress, clientAddress) -> {
            largestMessage.accumulateAndGet(binaryRequest.getBytes().length, Math::max);
            try {
                listenerMayReturn.await(120, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                listenerReturned(binaryRequest.getBytes().length);
            }
        });
        long toSend = 16L * 1024 * 1024;
        AtomicLong bytesWritten = new AtomicLong();
        AtomicReference<Throwable> writeFailure = new AtomicReference<>();
        AtomicBoolean stopWriting = new AtomicBoolean();

        try (Socket client = new Socket("127.0.0.1", mockServer.getLocalPort())) {
            client.setSendBufferSize(64 * 1024);
            Thread writer = new Thread(() -> {
                try {
                    OutputStream output = client.getOutputStream();
                    byte[] chunk = new byte[1024];
                    Arrays.fill(chunk, (byte) 'x');
                    chunk[0] = 'H';
                    while (bytesWritten.get() < toSend && !stopWriting.get()) {
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
            try {
                long writtenWhenHeldBack = awaitNoLongerGrowing(bytesWritten, toSend);
                assertThat("the client can no longer write: it is held back, not queued", writtenWhenHeldBack, lessThan(toSend));
                // every message read is waiting for the listener, which has not returned once, and each was forwarded
                assertThat("the messages waiting for the listener are bounded", upstream.accepted.get(), lessThanOrEqualTo(MAX_PENDING_LISTENER_CALLS + 1));
                assertThat("and each is at most one gathered read, so the memory they hold is too", largestMessage.get(), lessThanOrEqualTo(BinaryMessageGatherer.MAX_GATHERED_BYTES));
                assertThat(upstream.mostOpen.get(), lessThanOrEqualTo(MAX_UNANSWERED_FORWARDS + CLOSE_LAG));

                listenerMayReturn.countDown();
                writer.join(TimeUnit.SECONDS.toMillis(120));
                assertThat("the client finishes once the listener catches up", writer.isAlive(), is(false));
                assertThat(writeFailure.get(), is(nullValue()));
                await("everything reaches the upstream", () -> upstream.bytesReceived.get() == toSend);
                await("and is reported to the listener", () -> bytesReported.get() == toSend);
                assertThat(upstream.mostOpen.get(), lessThanOrEqualTo(MAX_UNANSWERED_FORWARDS + CLOSE_LAG));
            } finally {
                stopWriting.set(true);
                listenerMayReturn.countDown();
            }
        }
    }
}
