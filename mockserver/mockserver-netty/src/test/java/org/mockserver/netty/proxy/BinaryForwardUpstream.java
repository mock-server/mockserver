package org.mockserver.netty.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.AttributeKey;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * The upstream of a per-message binary forward in an integration test: accepts every connection, reads everything sent
 * on it, and never closes it. A silent one never writes; an answering one writes {@code reply} once per connection,
 * when it first reads from it. Also the waits those tests share.
 */
final class BinaryForwardUpstream {

    private static final AttributeKey<Boolean> COUNTED = AttributeKey.valueOf("BINARY_FORWARD_UPSTREAM_COUNTED");
    private final EventLoopGroup group = new NioEventLoopGroup(1);
    final AtomicInteger accepted = new AtomicInteger();
    final AtomicInteger open = new AtomicInteger();
    final AtomicInteger mostOpen = new AtomicInteger();
    final AtomicLong bytesReceived = new AtomicLong();
    private final Channel server;

    static BinaryForwardUpstream silent() {
        return new BinaryForwardUpstream(null);
    }

    static BinaryForwardUpstream answeringEachConnectionWith(byte[] reply) {
        return new BinaryForwardUpstream(reply);
    }

    private BinaryForwardUpstream(byte[] reply) {
        try {
            server = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            private boolean answered;

                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                ctx.channel().attr(COUNTED).set(Boolean.TRUE);
                                accepted.incrementAndGet();
                                mostOpen.accumulateAndGet(open.incrementAndGet(), Math::max);
                            }

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf message) {
                                bytesReceived.addAndGet(message.readableBytes());
                                if (reply != null && !answered) {
                                    answered = true;
                                    ctx.writeAndFlush(Unpooled.wrappedBuffer(reply));
                                }
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                if (ctx.channel().attr(COUNTED).getAndSet(null) != null) {
                                    open.decrementAndGet();
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                ctx.close();
                            }
                        });
                    }
                })
                .bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    int port() {
        return ((InetSocketAddress) server.localAddress()).getPort();
    }

    void close() throws InterruptedException {
        server.close().sync();
        group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
    }

    /** @return true when nothing arrives before the read times out: not a byte, and not the end of the stream */
    static boolean readsNothing(InputStream input) throws Exception {
        try {
            input.read();
            return false;
        } catch (SocketTimeoutException nothingToRead) {
            return true;
        }
    }

    static void await(String what, BooleanSupplier condition) throws InterruptedException {
        await(what, TimeUnit.SECONDS.toMillis(60), condition);
    }

    static void await(String what, long withinMillis, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(withinMillis);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("not within " + withinMillis + " ms: " + what);
            }
            TimeUnit.MILLISECONDS.sleep(2);
        }
    }

    /** @return the value once it has stopped changing for two seconds, or has reached {@code limit} */
    static long awaitNoLongerGrowing(AtomicLong value, long limit) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
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
            } else if (now > 0 && System.nanoTime() - unchangedSince > TimeUnit.SECONDS.toNanos(2)) {
                return now;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        throw new AssertionError("still changing after 120 seconds, last " + last);
    }
}
