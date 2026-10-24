package org.mockserver.netty.unification;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.model.TcpChaosProfile;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.TcpChaosProfile.tcpChaosProfile;

/**
 * TCP chaos latency and bandwidth queue inbound reads in arrival order and pause the connection's reads while
 * more than {@link TcpChaosHandler#PAUSE_READS_ABOVE_QUEUED_BYTES} are queued, so a fast sender is held back by
 * TCP flow control instead of growing the queue without limit.
 */
public class TcpChaosHandlerTest {

    private static NioEventLoopGroup group;

    @BeforeClass
    public static void createEventLoop() {
        group = new NioEventLoopGroup(2);
    }

    @AfterClass
    public static void stopEventLoop() {
        group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }

    @Test
    public void shouldPauseReadsAboveTheQueuedByteThresholdAndDeliverEveryByteInOrder() {
        TcpChaosHandler chaos = new TcpChaosHandler(channel -> tcpChaosProfile().withLatencyMs(100L));
        Collector collector = new Collector();
        EmbeddedChannel channel = new EmbeddedChannel(chaos, collector);
        channel.freezeTime();
        byte[] sent = randomBytes(64 * 4096);

        for (int offset = 0; offset < sent.length; offset += 4096) {
            channel.writeInbound(Unpooled.copiedBuffer(sent, offset, 4096));
            if (offset + 4096 > TcpChaosHandler.PAUSE_READS_ABOVE_QUEUED_BYTES) {
                assertThat(channel.config().isAutoRead(), is(false));
            } else {
                assertThat(channel.config().isAutoRead(), is(true));
            }
        }
        assertThat("nothing delivered before the latency", collector.bytes.size(), is(0));

        channel.advanceTimeBy(99, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat(collector.bytes.size(), is(0));

        int readCompletesBefore = collector.readCompletes;
        channel.advanceTimeBy(1, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat(collector.bytes.toByteArray(), is(sent));
        assertThat(chaos.queuedBytes(), is(0L));
        assertThat("reads resume once drained", channel.config().isAutoRead(), is(true));
        assertThat("the delayed batch ends with a read-complete", collector.readCompletes, greaterThan(readCompletesBefore));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldMarkTheConnectionClosingBeforeClosingItPastTheDataLimit() {
        TcpChaosHandler chaos = new TcpChaosHandler(channel -> tcpChaosProfile().withLimitDataBytes(10L));
        Collector collector = new Collector();
        EmbeddedChannel channel = new EmbeddedChannel(chaos, collector);

        channel.writeInbound(Unpooled.copiedBuffer(filled(10, 'a')));
        assertThat(org.mockserver.socket.ConnectionClosing.isMarked(channel), is(false));
        channel.writeInbound(Unpooled.copiedBuffer(filled(1, 'b')));

        // the close starts beneath the pipelining gate, so the gate must be told it is coming
        assertThat(org.mockserver.socket.ConnectionClosing.isMarked(channel), is(true));
        assertThat(channel.isOpen(), is(false));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldDeliverBandwidthLimitedReadsInArrivalOrderAtTheLinkRate() {
        TcpChaosHandler chaos = new TcpChaosHandler(channel -> tcpChaosProfile().withBandwidthBytesPerSec(10_000L));
        Collector collector = new Collector();
        EmbeddedChannel channel = new EmbeddedChannel(chaos, collector);
        channel.freezeTime();

        channel.writeInbound(Unpooled.copiedBuffer(filled(1_000, 'a')));
        channel.writeInbound(Unpooled.copiedBuffer(filled(5_000, 'b')));
        // a small read after a large one used to overtake it
        channel.writeInbound(Unpooled.copiedBuffer(filled(1_000, 'c')));

        channel.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat(new String(collector.bytes.toByteArray()), is(new String(filled(1_000, 'a'))));

        // the 5,000 bytes take 500 ms on the link after the first 1,000 bytes' 100 ms
        channel.advanceTimeBy(499, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat(collector.bytes.size(), is(1_000));

        channel.advanceTimeBy(1, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat(collector.bytes.size(), is(6_000));

        channel.advanceTimeBy(99, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat(collector.bytes.size(), is(6_000));

        channel.advanceTimeBy(1, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        byte[] expected = new byte[7_000];
        System.arraycopy(filled(1_000, 'a'), 0, expected, 0, 1_000);
        System.arraycopy(filled(5_000, 'b'), 0, expected, 1_000, 5_000);
        System.arraycopy(filled(1_000, 'c'), 0, expected, 6_000, 1_000);
        assertThat(collector.bytes.toByteArray(), is(expected));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldKeepLaterUndelayedReadsBehindQueuedOnes() {
        AtomicBoolean latency = new AtomicBoolean(true);
        TcpChaosHandler chaos = new TcpChaosHandler(channel -> latency.get() ? tcpChaosProfile().withLatencyMs(50L) : null);
        Collector collector = new Collector();
        EmbeddedChannel channel = new EmbeddedChannel(chaos, collector);
        channel.freezeTime();

        channel.writeInbound(Unpooled.copiedBuffer(filled(10, 'a')));
        latency.set(false);
        channel.writeInbound(Unpooled.copiedBuffer(filled(10, 'b')));
        assertThat("the profile was removed, but earlier bytes are still queued", collector.bytes.size(), is(0));

        channel.advanceTimeBy(50, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        assertThat(new String(collector.bytes.toByteArray()), is(new String(filled(10, 'a')) + new String(filled(10, 'b'))));
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldReleaseQueuedBuffersWhenTheConnectionCloses() {
        TcpChaosHandler chaos = new TcpChaosHandler(channel -> tcpChaosProfile().withLatencyMs(10_000L));
        EmbeddedChannel channel = new EmbeddedChannel(chaos, new Collector());
        List<ByteBuf> buffers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ByteBuf buffer = Unpooled.buffer(8192).writeBytes(new byte[8192]);
            buffers.add(buffer);
            channel.writeInbound(buffer);
        }

        channel.close().syncUninterruptibly();

        for (ByteBuf buffer : buffers) {
            assertThat(buffer.refCnt(), is(0));
        }
        assertThat(chaos.queuedBytes(), is(0L));
        channel.finishAndReleaseAll();
    }

    @Test(timeout = 60_000)
    public void shouldBoundQueuedBytesForAFastSenderOnARealSocketAndDeliverEveryByte() throws Exception {
        byte[] sent = randomBytes(4 * 1024 * 1024);
        AtomicLong maxQueued = new AtomicLong();
        AtomicBoolean paused = new AtomicBoolean();
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        CompletableFuture<Void> allReceived = new CompletableFuture<>();

        Channel server = new ServerBootstrap()
            .group(group)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    TcpChaosHandler chaos = new TcpChaosHandler(channel -> tcpChaosProfile().withLatencyMs(20L));
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ctx.fireChannelRead(msg);
                            maxQueued.accumulateAndGet(chaos.queuedBytes(), Math::max);
                            if (chaos.readsPaused()) {
                                paused.set(true);
                            }
                        }
                    }, chaos, new SimpleChannelInboundHandler<ByteBuf>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                            byte[] copy = new byte[msg.readableBytes()];
                            msg.readBytes(copy);
                            synchronized (received) {
                                received.write(copy, 0, copy.length);
                                if (received.size() == sent.length) {
                                    allReceived.complete(null);
                                }
                            }
                        }
                    });
                }
            })
            // 127.0.0.1 itself: bound to the wildcard, the port can be one another process listens on at 127.0.0.1
            .bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();
        try (Socket socket = new Socket("127.0.0.1", ((InetSocketAddress) server.localAddress()).getPort())) {
            OutputStream outputStream = socket.getOutputStream();
            for (int offset = 0; offset < sent.length; offset += 64 * 1024) {
                outputStream.write(sent, offset, Math.min(64 * 1024, sent.length - offset));
            }
            outputStream.flush();
            allReceived.get(45, TimeUnit.SECONDS);
        } finally {
            server.close().sync();
        }

        synchronized (received) {
            assertThat("every byte arrived, in order", Arrays.equals(received.toByteArray(), sent), is(true));
        }
        assertThat(paused.get(), is(true));
        // at most the pause threshold plus the one socket read that crossed it (Netty reads at most 64 KiB)
        assertThat(maxQueued.get(), lessThanOrEqualTo((long) TcpChaosHandler.PAUSE_READS_ABOVE_QUEUED_BYTES + 64 * 1024));
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new Random(42).nextBytes(bytes);
        return bytes;
    }

    private static byte[] filled(int size, char c) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) c);
        return bytes;
    }

    private static final class Collector extends ChannelInboundHandlerAdapter {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private int readCompletes;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                byte[] copy = new byte[buf.readableBytes()];
                buf.readBytes(copy);
                bytes.write(copy, 0, copy.length);
            } finally {
                buf.release();
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            readCompletes++;
        }
    }
}
