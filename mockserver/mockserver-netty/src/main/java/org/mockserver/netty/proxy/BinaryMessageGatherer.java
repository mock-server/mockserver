package org.mockserver.netty.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.mockserver.netty.unification.BinaryAwareRecvByteBufAllocator;

/**
 * Joins what one read loop delivers on a binary connection into one message, so that a message is not cut where a
 * read buffer filled or where TLS handed over part of what it had decrypted. Without a protocol's framing the read
 * loop is the nearest thing to a message boundary there is: it ends when the socket has nothing more.
 * One per connection, in front of {@link BinaryRequestProxyingHandler}.
 */
public class BinaryMessageGatherer extends ChannelInboundHandlerAdapter {

    /**
     * The most one message holds; what a read loop delivers beyond it starts the next message, so one message is at
     * most this much in one write upstream and one listener call.
     */
    static final int MAX_GATHERED_BYTES = 256 * 1024;

    // the message so far: its only piece, or its pieces joined once there are two
    private ByteBuf first;
    private CompositeByteBuf joined;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf)) {
            ctx.fireChannelRead(msg);
            return;
        }
        ByteBuf piece = (ByteBuf) msg;
        if (!BinaryAwareRecvByteBufAllocator.isReading(ctx.channel())) {
            // not read from the socket just now (bytes that were held and are given up, say): a message as it is
            deliver(ctx);
            ctx.fireChannelRead(piece);
            return;
        }
        while (held() + piece.readableBytes() > MAX_GATHERED_BYTES) {
            hold(ctx, piece.readRetainedSlice(MAX_GATHERED_BYTES - held()));
            deliver(ctx);
        }
        if (piece.isReadable()) {
            hold(ctx, piece);
            if (held() == MAX_GATHERED_BYTES) {
                deliver(ctx);
            }
        } else {
            piece.release();
        }
    }

    private int held() {
        return joined != null ? joined.readableBytes() : first != null ? first.readableBytes() : 0;
    }

    private void hold(ChannelHandlerContext ctx, ByteBuf piece) {
        if (first == null) {
            first = piece;
        } else {
            if (joined == null) {
                // no limit on components: joining must never copy what is already held
                joined = ctx.alloc().compositeBuffer(Integer.MAX_VALUE).addComponent(true, first);
            }
            joined.addComponent(true, piece);
        }
    }

    private ByteBuf take() {
        ByteBuf message = joined != null ? joined : first;
        first = null;
        joined = null;
        return message;
    }

    private void deliver(ChannelHandlerContext ctx) {
        ByteBuf message = take();
        if (message != null) {
            ctx.fireChannelRead(message);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        deliver(ctx);
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // a client that sent and closed still has what it sent taken as a message
        deliver(ctx);
        ctx.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        // passed on, not dropped: whatever is next in the pipeline, or its end, releases it
        deliver(ctx);
    }
}
