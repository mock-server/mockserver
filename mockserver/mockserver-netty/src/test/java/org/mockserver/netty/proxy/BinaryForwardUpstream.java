package org.mockserver.netty.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * The upstream of a binary forward in an integration test: accepts every connection, reads everything sent on it,
 * answers each read with {@code reply}, and never closes it. Like a server that writes with blocking calls, it stops
 * reading a connection while that connection cannot take more of its replies. Also the waits those tests share.
 */
final class BinaryForwardUpstream {

    private final EventLoopGroup group = new NioEventLoopGroup(1);
    final AtomicInteger accepted = new AtomicInteger();
    final AtomicLong bytesReceived = new AtomicLong();
    final AtomicLong replyBytesWritten = new AtomicLong();
    private final Channel server;

    static BinaryForwardUpstream answeringEachReadWith(byte[] reply) {
        return new BinaryForwardUpstream(reply);
    }

    private BinaryForwardUpstream(byte[] reply) {
        try {
            server = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                // so little of what MockServer does not read waits in this side's kernel buffers
                .childOption(ChannelOption.SO_SNDBUF, 16 * 1024)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                accepted.incrementAndGet();
                            }

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf message) {
                                bytesReceived.addAndGet(message.readableBytes());
                                replyBytesWritten.addAndGet(reply.length);
                                ctx.writeAndFlush(Unpooled.wrappedBuffer(reply));
                                if (!ctx.channel().isWritable()) {
                                    ctx.channel().config().setAutoRead(false);
                                }
                            }

                            @Override
                            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                                if (ctx.channel().isWritable()) {
                                    ctx.channel().config().setAutoRead(true);
                                }
                                ctx.fireChannelWritabilityChanged();
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
