package org.mockserver.netty.proxy;

import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.netty.MockServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.netty.proxy.BinaryForwardUpstream.await;
import static org.mockserver.netty.proxy.BinaryForwardUpstream.awaitNoLongerGrowing;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * A client that sends binary messages, relayed on one upstream connection, and never reads the replies. Each small
 * message brings a much larger reply, so without a bound the replies MockServer holds for the client grow with every
 * message. MockServer stops reading the upstream while it cannot write to the client, and stops reading the client
 * while it cannot write to the upstream, so what it holds stays bounded and none is lost once the client reads.
 */
public class BinaryForwardClientNotReadingIntegrationTest {

    private static final int REPLY_BYTES = 2000;
    private static final int MESSAGE_BYTES = 64;
    private static final int MESSAGES = 12_000;
    /**
     * Replies produced for a client that reads none: what the kernel buffers on the way (on Linux by default up to
     * 6 MiB for MockServer's receive buffer from the upstream and 4 MiB for its send buffer to the client) and each
     * connection's write buffer. Under half of what every message's reply would come to.
     */
    private static final long MOST_REPLY_BYTES_HELD = 11L * 1024 * 1024;

    private final AtomicLong bytesWritten = new AtomicLong();
    private final AtomicReference<Throwable> writeFailure = new AtomicReference<>();
    private final AtomicBoolean stopWriting = new AtomicBoolean();
    private BinaryForwardUpstream upstream;
    private MockServer mockServer;

    @After
    public void stop() throws InterruptedException {
        stopWriting.set(true);
        stopQuietly(mockServer);
        if (upstream != null) {
            upstream.close();
        }
    }

    @Test
    public void shouldNotReadAClientThatDoesNotReadItsReplies() throws Exception {
        byte[] reply = new byte[REPLY_BYTES];
        Arrays.fill(reply, (byte) 'r');
        upstream = BinaryForwardUpstream.answeringEachReadWith(reply);
        mockServer = new MockServer(Configuration.configuration()
            // each message is logged as it is read; the bytes logged with it are not what is tested
            .maxLoggedBodyBytes(64), upstream.port(), "127.0.0.1", 0);

        try (Socket client = new Socket()) {
            // so the kernel holds little of what the client does not read, and MockServer has to hold the rest
            client.setReceiveBufferSize(4 * 1024);
            client.setTcpNoDelay(true);
            client.connect(new InetSocketAddress("127.0.0.1", mockServer.getLocalPort()), 10_000);
            Thread writer = clientSendingOneMessageAtATime(client);
            try {
                long forwardedWhileNotReading = awaitNoLongerGrowing(upstream.bytesReceived, (long) MESSAGES * MESSAGE_BYTES);
                assertThat("a client that reads none of its replies is not read either", forwardedWhileNotReading, lessThan((long) MESSAGES * MESSAGE_BYTES));
                assertThat("so the replies held for it are bounded", upstream.replyBytesWritten.get(), lessThanOrEqualTo(MOST_REPLY_BYTES_HELD));

                stopWriting.set(true);
                writer.join(TimeUnit.SECONDS.toMillis(60));
                assertThat(writer.isAlive(), is(false));
                assertThat(writeFailure.get(), is(nullValue()));

                AtomicLong repliesRead = new AtomicLong();
                Thread reader = clientReading(client, repliesRead);
                await("once the client reads, what it sent is forwarded", () -> upstream.bytesReceived.get() == bytesWritten.get());
                await("and every reply reaches it", () -> repliesRead.get() == upstream.replyBytesWritten.get());
                assertThat("on a connection still open", reader.isAlive(), is(true));
                assertThat("all on one upstream connection", upstream.accepted.get(), is(1));
            } finally {
                stopWriting.set(true);
            }
        }
    }

    /** Sends each message once the one before has reached the upstream, so each is answered with a reply of its own. */
    private Thread clientSendingOneMessageAtATime(Socket client) {
        Thread writer = new Thread(() -> {
            try {
                OutputStream output = client.getOutputStream();
                byte[] message = new byte[MESSAGE_BYTES];
                Arrays.fill(message, (byte) 'x');
                message[0] = 'H';
                for (int i = 0; i < MESSAGES && !stopWriting.get(); i++) {
                    output.write(message);
                    output.flush();
                    long written = bytesWritten.addAndGet(message.length);
                    while (upstream.bytesReceived.get() < written && !stopWriting.get()) {
                        LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(20));
                    }
                }
            } catch (Throwable throwable) {
                writeFailure.set(throwable);
            }
        }, "client-not-reading");
        writer.setDaemon(true);
        writer.start();
        return writer;
    }

    private static Thread clientReading(Socket client, AtomicLong repliesRead) {
        Thread reader = new Thread(() -> {
            try {
                InputStream input = client.getInputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    repliesRead.addAndGet(read);
                }
            } catch (Exception closed) {
                // the test is over
            }
        }, "client-reading");
        reader.setDaemon(true);
        reader.start();
        return reader;
    }
}
