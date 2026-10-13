package org.mockserver.netty.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.model.BinaryMessage;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.socket.ChannelReadPause;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static org.mockserver.netty.proxy.BinaryRequestProxyingHandler.MAX_UNANSWERED_FORWARDS;
import static org.mockserver.netty.proxy.BinaryRequestProxyingHandler.MAX_WAITING_BYTES;
import static org.mockserver.netty.proxy.BinaryRequestProxyingHandler.MAX_WAITING_MESSAGES;

/**
 * With {@code forwardBinaryRequestsWithoutWaitingForResponse} a connection's messages wait for the one being
 * forwarded. A client that stays ahead of the upstream is no longer read, so what waits is bounded and nothing is
 * dropped.
 */
public class BinaryRequestProxyingHandlerBackpressureTest {

    private final Configuration configuration = configuration().forwardBinaryRequestsUseSingleConnection(false).forwardBinaryRequestsWithoutWaitingForResponse(true);
    private final NettyHttpClient httpClient = mock(NettyHttpClient.class);
    private final List<Integer> forwardsStarted = new CopyOnWriteArrayList<>();
    private final List<Consumer<Throwable>> requestSent = new CopyOnWriteArrayList<>();
    private final List<CompletableFuture<BinaryMessage>> responses = new ArrayList<>();
    private int nextMessage;

    /** An upstream that reports nothing sent until the test says so. */
    private void upstreamThatDoesNotAcceptYet() {
        when(httpClient.sendRequest(any(BinaryMessage.class), anyBoolean(), any(InetSocketAddress.class), any(), any()))
            .thenAnswer(invocation -> {
                requestSent.add(invocation.getArgument(4));
                forwardsStarted.add(sequenceNumber(invocation.<BinaryMessage>getArgument(0).getBytes()));
                return new CompletableFuture<BinaryMessage>();
            });
    }

    private BinaryRequestProxyingHandler handler() {
        return new BinaryRequestProxyingHandler(configuration, mock(MockServerLogger.class), mock(Scheduler.class), httpClient, mock(HttpState.class));
    }

    private EmbeddedChannel clientConnection() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(REMOTE_SOCKET).set(new InetSocketAddress("127.0.0.1", 1234));
        channel.pipeline().addLast(handler());
        return channel;
    }

    /** Each message starts with its position among those the client sent, so order and loss can both be seen. */
    private void clientSends(EmbeddedChannel channel, int length) {
        ByteBuf message = Unpooled.buffer(length).writeInt(nextMessage++).writeZero(length - Integer.BYTES);
        channel.writeInbound(message);
    }

    private static int sequenceNumber(byte[] message) {
        return Unpooled.wrappedBuffer(message).readInt();
    }

    private static boolean isReading(EmbeddedChannel channel) {
        return channel.config().isAutoRead() && ChannelReadPause.holds(channel) == 0;
    }

    private void assertEveryMessageWasForwardedInOrder() {
        assertThat("every message the client sent was forwarded", forwardsStarted, hasSize(nextMessage));
        for (int i = 0; i < nextMessage; i++) {
            assertThat("in the order they were sent", forwardsStarted.get(i), is(i));
        }
    }

    @Test
    public void shouldStopReadingTheClientOnlyOnceMoreMessagesWaitThanTheLimit() {
        upstreamThatDoesNotAcceptYet();
        EmbeddedChannel channel = clientConnection();

        clientSends(channel, 8);
        for (int waiting = 0; waiting < MAX_WAITING_MESSAGES; waiting++) {
            clientSends(channel, 8);
        }
        assertThat("at the limit the client is still read", isReading(channel), is(true));

        clientSends(channel, 8);
        assertThat("one more than the limit and it is not", isReading(channel), is(false));

        // what a read already under way still delivers
        clientSends(channel, 8);
        clientSends(channel, 8);
        assertThat("the hold is taken once, however many more arrive", ChannelReadPause.holds(channel), is(1));

        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldStopReadingTheClientOnlyOnceMoreBytesWaitThanTheLimit() {
        upstreamThatDoesNotAcceptYet();
        EmbeddedChannel channel = clientConnection();

        clientSends(channel, 8);
        clientSends(channel, MAX_WAITING_BYTES - 8);
        clientSends(channel, 8);
        assertThat("at the limit the client is still read", isReading(channel), is(true));

        clientSends(channel, 8);
        assertThat("past the limit by one small message and it is not", isReading(channel), is(false));

        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldNotStopReadingForALargeMessageThatIsForwardedAtOnce() {
        upstreamThatDoesNotAcceptYet();
        EmbeddedChannel channel = clientConnection();

        clientSends(channel, MAX_WAITING_BYTES + 1);

        assertThat("it is being forwarded, not waiting", isReading(channel), is(true));
        assertEveryMessageWasForwardedInOrder();

        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldReadTheClientAgainOnceHalfOfWhatWaitedHasBeenForwardedLosingNothing() {
        upstreamThatDoesNotAcceptYet();
        EmbeddedChannel channel = clientConnection();
        clientSends(channel, 8);
        for (int waiting = 0; waiting < MAX_WAITING_MESSAGES + 1; waiting++) {
            clientSends(channel, 8);
        }
        assertThat(isReading(channel), is(false));

        // each message reported sent starts the next, so one fewer waits
        int sent = 0;
        for (int waiting = MAX_WAITING_MESSAGES; waiting > MAX_WAITING_MESSAGES / 2; waiting--) {
            requestSent.get(sent++).accept(null);
            assertThat("still not read with " + waiting + " waiting", isReading(channel), is(false));
        }
        requestSent.get(sent++).accept(null);
        assertThat("read again with " + MAX_WAITING_MESSAGES / 2 + " waiting", isReading(channel), is(true));

        // the client carries on, and the upstream now accepts each message as it is forwarded
        clientSends(channel, 8);
        clientSends(channel, 8);
        while (sent < requestSent.size()) {
            requestSent.get(sent++).accept(null);
        }

        assertEveryMessageWasForwardedInOrder();
        assertThat(isReading(channel), is(true));

        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldReadTheClientAgainOnceHalfOfTheBytesThatWaitedHaveBeenForwarded() {
        upstreamThatDoesNotAcceptYet();
        EmbeddedChannel channel = clientConnection();
        clientSends(channel, 8);
        clientSends(channel, MAX_WAITING_BYTES / 2);
        clientSends(channel, 8);
        clientSends(channel, MAX_WAITING_BYTES / 2);
        assertThat(isReading(channel), is(false));

        requestSent.get(0).accept(null);
        assertThat("half the limit and one small message still wait", isReading(channel), is(false));

        requestSent.get(1).accept(null);
        assertThat("exactly half the limit waits", isReading(channel), is(true));

        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldPauseAgainWhenTheClientGetsAheadAgain() {
        upstreamThatDoesNotAcceptYet();
        EmbeddedChannel channel = clientConnection();
        clientSends(channel, 8);
        for (int waiting = 0; waiting < MAX_WAITING_MESSAGES + 1; waiting++) {
            clientSends(channel, 8);
        }
        for (int sent = 0; sent <= MAX_WAITING_MESSAGES / 2; sent++) {
            requestSent.get(sent).accept(null);
        }
        assertThat(isReading(channel), is(true));

        for (int waiting = MAX_WAITING_MESSAGES / 2; waiting < MAX_WAITING_MESSAGES; waiting++) {
            clientSends(channel, 8);
        }
        assertThat("back at the limit", isReading(channel), is(true));
        clientSends(channel, 8);

        assertThat(isReading(channel), is(false));
        assertThat(ChannelReadPause.holds(channel), is(1));

        channel.finishAndReleaseAll();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldReadTheClientAgainWhenAForwardFailsWhileItIsNotBeingRead() {
        when(httpClient.sendRequest(any(BinaryMessage.class), anyBoolean(), any(InetSocketAddress.class), any(), any()))
            .thenAnswer(invocation -> {
                requestSent.add(invocation.getArgument(4));
                return new CompletableFuture<BinaryMessage>();
            });
        // a scheduler that is handed each response but does not act on a failure, so the connection is left
        // open and what is seen is the queue's own doing
        Scheduler scheduler = mock(Scheduler.class);
        doAnswer(invocation -> responses.add(invocation.getArgument(0)))
            .when(scheduler).submit(any(CompletableFuture.class), any(Runnable.class), anyBoolean());
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(REMOTE_SOCKET).set(new InetSocketAddress("127.0.0.1", 1234));
        channel.pipeline().addLast(new BinaryRequestProxyingHandler(configuration, mock(MockServerLogger.class), scheduler, httpClient, mock(HttpState.class)));
        clientSends(channel, 8);
        for (int waiting = 0; waiting < MAX_WAITING_MESSAGES + 1; waiting++) {
            clientSends(channel, 8);
        }
        assertThat(isReading(channel), is(false));

        requestSent.get(0).accept(new IOException("connection refused"));

        assertThat("nothing waits any longer, so nothing holds the client back", isReading(channel), is(true));
        assertThat("no message behind the failure was attempted", requestSent, hasSize(1));
        assertThat(responses, hasSize(MAX_WAITING_MESSAGES + 2));
        assertThat(failureOf(responses.get(0)), instanceOf(IOException.class));
        for (CompletableFuture<BinaryMessage> notAttempted : responses.subList(1, responses.size())) {
            assertThat("each is failed rather than left waiting", failureOf(notAttempted), instanceOf(BinaryRequestProxyingHandler.NotForwardedException.class));
        }

        channel.finishAndReleaseAll();
    }

    private static Throwable failureOf(CompletableFuture<BinaryMessage> response) {
        assertThat(response.isCompletedExceptionally(), is(true));
        return assertThrows(ExecutionException.class, response::get).getCause();
    }

    @Test
    public void shouldForwardEveryMessageOfAClientThatClosesWhileItIsNotBeingRead() {
        upstreamThatDoesNotAcceptYet();
        EmbeddedChannel channel = clientConnection();
        clientSends(channel, 8);
        for (int waiting = 0; waiting < MAX_WAITING_MESSAGES + 1; waiting++) {
            clientSends(channel, 8);
        }
        assertThat(isReading(channel), is(false));

        channel.close();
        assertThat("the hold is given up with the connection", ChannelReadPause.holds(channel), is(0));
        for (int sent = 0; sent < requestSent.size(); sent++) {
            requestSent.get(sent).accept(null);
        }

        assertEveryMessageWasForwardedInOrder();
        assertThat("and is not given up a second time as the queue drains", ChannelReadPause.holds(channel), is(0));
        channel.checkException();
    }

    @Test
    public void shouldReadTheClientAgainWhenTheHandlerIsRemovedWhileItIsNotBeingRead() {
        upstreamThatDoesNotAcceptYet();
        EmbeddedChannel channel = clientConnection();
        // another holder of the connection's reads, which removing the handler must leave alone
        ChannelReadPause.pause(channel);
        clientSends(channel, 8);
        for (int waiting = 0; waiting < MAX_WAITING_MESSAGES + 1; waiting++) {
            clientSends(channel, 8);
        }
        assertThat(ChannelReadPause.holds(channel), is(2));

        channel.pipeline().remove(BinaryRequestProxyingHandler.class);
        assertThat("only its own hold is given up", ChannelReadPause.holds(channel), is(1));

        for (int sent = 0; sent < requestSent.size(); sent++) {
            requestSent.get(sent).accept(null);
        }
        assertEveryMessageWasForwardedInOrder();
        assertThat("and not a second time as the queue drains", ChannelReadPause.holds(channel), is(1));

        ChannelReadPause.resume(channel);
        assertThat(isReading(channel), is(true));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldNotHoldBackAClientWhenWaitingForEachResponse() {
        Configuration waiting = configuration().forwardBinaryRequestsUseSingleConnection(false).forwardBinaryRequestsWithoutWaitingForResponse(false);
        when(httpClient.sendRequest(any(BinaryMessage.class), anyBoolean(), any(InetSocketAddress.class), any()))
            .thenAnswer(invocation -> {
                forwardsStarted.add(sequenceNumber(invocation.<BinaryMessage>getArgument(0).getBytes()));
                return new CompletableFuture<BinaryMessage>();
            });
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(REMOTE_SOCKET).set(new InetSocketAddress("127.0.0.1", 1234));
        channel.pipeline().addLast(new BinaryRequestProxyingHandler(waiting, mock(MockServerLogger.class), mock(Scheduler.class), httpClient, mock(HttpState.class)));

        // below that mode's own limit, on forwards not yet answered (BinaryRequestProxyingHandlerWaitingBoundTest)
        for (int message = 0; message < MAX_UNANSWERED_FORWARDS - 1; message++) {
            clientSends(channel, 8);
        }

        assertThat("that mode forwards each message at once and queues nothing", isReading(channel), is(true));
        assertEveryMessageWasForwardedInOrder();

        channel.finishAndReleaseAll();
    }

    /**
     * A real connection, so that not reading it is seen to reach the client: it can no longer write. The client
     * sends far more than the limit while the upstream accepts nothing, then the upstream accepts slowly.
     */
    @Test
    public void shouldHoldAFastClientBackRatherThanQueueWhatItSends() throws Exception {
        int readSize = 8 * 1024;
        int toSend = 8 * 1024 * 1024;
        AtomicBoolean upstreamAccepts = new AtomicBoolean(false);
        AtomicReference<Consumer<Throwable>> notYetReportedSent = new AtomicReference<>();
        AtomicLong bytesRead = new AtomicLong();
        AtomicLong bytesForwardStarted = new AtomicLong();
        AtomicLong mostBytesWaiting = new AtomicLong();
        AtomicLong messagesRead = new AtomicLong();
        AtomicLong forwards = new AtomicLong();
        AtomicLong mostMessagesWaiting = new AtomicLong();
        AtomicLong firstByteOutOfOrder = new AtomicLong(-1);
        EventLoopGroup upstreamThread = new NioEventLoopGroup(1);
        when(httpClient.sendRequest(any(BinaryMessage.class), anyBoolean(), any(InetSocketAddress.class), any(), any()))
            .thenAnswer(invocation -> {
                byte[] message = invocation.<BinaryMessage>getArgument(0).getBytes();
                long position = bytesForwardStarted.get();
                for (int i = 0; i < message.length && firstByteOutOfOrder.get() < 0; i++) {
                    if (message[i] != byteAt(position + i)) {
                        firstByteOutOfOrder.set(position + i);
                    }
                }
                bytesForwardStarted.addAndGet(message.length);
                forwards.incrementAndGet();
                Consumer<Throwable> reportSent = invocation.getArgument(4);
                if (upstreamAccepts.get()) {
                    // slower than the client, and from a thread that is not the connection's
                    upstreamThread.schedule(() -> reportSent.accept(null), 200, TimeUnit.MICROSECONDS);
                } else {
                    notYetReportedSent.set(reportSent);
                }
                return new CompletableFuture<BinaryMessage>();
            });
        BinaryRequestProxyingHandler handler = handler();
        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Channel server = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_RCVBUF, 32 * 1024)
                .childOption(ChannelOption.RECVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(readSize))
                .childAttr(REMOTE_SOCKET, new InetSocketAddress("127.0.0.1", 1234))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object message) {
                                bytesRead.addAndGet(((ByteBuf) message).readableBytes());
                                messagesRead.incrementAndGet();
                                ctx.fireChannelRead(message);
                                // on the connection's own thread, as the forwards are started, so the two agree
                                mostBytesWaiting.accumulateAndGet(bytesRead.get() - bytesForwardStarted.get(), Math::max);
                                mostMessagesWaiting.accumulateAndGet(messagesRead.get() - forwards.get(), Math::max);
                            }
                        });
                        channel.pipeline().addLast(handler);
                    }
                })
                .bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 0)).sync().channel();
            AtomicLong bytesWritten = new AtomicLong();
            AtomicReference<Throwable> writeFailure = new AtomicReference<>();
            try (Socket client = new Socket()) {
                client.setSendBufferSize(32 * 1024);
                client.connect(server.localAddress(), 10_000);
                Thread writer = new Thread(() -> {
                    try {
                        OutputStream output = client.getOutputStream();
                        byte[] chunk = new byte[1024];
                        while (bytesWritten.get() < toSend) {
                            for (int i = 0; i < chunk.length; i++) {
                                chunk[i] = byteAt(bytesWritten.get() + i);
                            }
                            output.write(chunk);
                            output.flush();
                            bytesWritten.addAndGet(chunk.length);
                        }
                    } catch (Throwable throwable) {
                        writeFailure.set(throwable);
                    }
                }, "fast-client");
                writer.setDaemon(true);
                writer.start();

                // which limit is passed first depends on how the reads fall: full ones reach the bytes, short ones the messages
                awaitPastALimit(mostBytesWaiting, mostMessagesWaiting);
                long writtenWhenHeldBack = awaitNoLongerGrowing(bytesWritten, toSend);
                assertThat("the client can no longer write: it is held back, not queued", writtenWhenHeldBack, lessThan((long) toSend));
                assertThat("what waits is the limit, and at most one read past it", mostBytesWaiting.get(), lessThanOrEqualTo((long) MAX_WAITING_BYTES + readSize));
                assertThat(mostMessagesWaiting.get(), lessThanOrEqualTo((long) MAX_WAITING_MESSAGES + 1));

                upstreamAccepts.set(true);
                notYetReportedSent.get().accept(null);

                writer.join(TimeUnit.SECONDS.toMillis(60));
                assertThat("the client finishes once the upstream accepts", writer.isAlive(), is(false));
                assertThat(writeFailure.get(), is(nullValue()));
                awaitAtLeast(bytesForwardStarted, toSend);

                assertThat("nothing the client sent was lost", bytesForwardStarted.get(), is((long) toSend));
                assertThat("and it was forwarded in the order it was sent", firstByteOutOfOrder.get(), is(-1L));
                assertThat("what waited stayed bounded all the way through", mostBytesWaiting.get(), lessThanOrEqualTo((long) MAX_WAITING_BYTES + readSize));
                assertThat(mostMessagesWaiting.get(), lessThanOrEqualTo((long) MAX_WAITING_MESSAGES + 1));
            } finally {
                server.close().sync();
            }
        } finally {
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
            upstreamThread.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
        }
    }

    private static byte byteAt(long position) {
        return (byte) (position * 31 + (position >>> 11));
    }

    /** @return the value once it has stopped changing for a second, or has reached {@code limit} */
    private static long awaitNoLongerGrowing(AtomicLong value, long limit) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        long last = -1;
        long unchangedSince = System.nanoTime();
        while (System.nanoTime() < deadline) {
            long now = value.get();
            if (now >= limit) {
                return now;
            }
            if (now != last) {
                last = now;
                unchangedSince = System.nanoTime();
            } else if (now > 0 && System.nanoTime() - unchangedSince > TimeUnit.SECONDS.toNanos(1)) {
                return now;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        throw new AssertionError("still changing after 60 seconds, last " + last);
    }

    private static void awaitPastALimit(AtomicLong bytesWaiting, AtomicLong messagesWaiting) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (bytesWaiting.get() <= MAX_WAITING_BYTES && messagesWaiting.get() <= MAX_WAITING_MESSAGES && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
        assertThat("more than a limit's worth waits within 60 seconds", bytesWaiting.get() > MAX_WAITING_BYTES || messagesWaiting.get() > MAX_WAITING_MESSAGES, is(true));
    }

    private static void awaitAtLeast(AtomicLong value, long atLeast) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (value.get() < atLeast && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
    }
}
