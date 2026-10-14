package org.mockserver.netty.proxy;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ScheduledFuture;
import org.mockserver.netty.proxy.relay.BinaryRelay;
import org.mockserver.socket.ChannelReadPause;

import java.util.ArrayDeque;
import java.util.Deque;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * The replies MockServer writes itself on one binary connection (a binary expectation's response, and the "unknown
 * message format" text), written in the order of the messages they answer. A reply with a delay is written once
 * that delay, counted from when its message was handled, has passed and every earlier reply has been written; a
 * reply without one is written at once unless an earlier reply is still waiting. The wait is a timer on the
 * connection's event loop, cancelled when the connection closes. Used only on that event loop.
 * <p>
 * The connection is not read while it is not writable, so a client that does not read its replies cannot make them
 * pile up in its outbound buffer: one {@link ChannelReadPause} hold, shared with the limit on waiting replies. A
 * relayed connection is left to {@link BinaryRelay}, which holds such a client back itself.
 */
final class BinaryLocalReplies {

    /**
     * The connection is not read while more replies than this wait, and is read again once they have all been
     * written and it is writable.
     */
    static final int MAX_WAITING_REPLIES = 64;
    private static final AttributeKey<BinaryLocalReplies> LOCAL_REPLIES = AttributeKey.valueOf("BINARY_LOCAL_REPLIES");

    private final ChannelHandlerContext ctx;
    private final Deque<Reply> waiting = new ArrayDeque<>();
    private ScheduledFuture<?> timer;
    private boolean readsPaused;
    private boolean closing;

    private BinaryLocalReplies(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Writes a reply now, or once its delay has passed and the replies before it have been written.
     *
     * @param closeAfter whether to close the connection once the reply has been written
     * @param afterWrite run once the reply has been written, or null
     */
    static void write(ChannelHandlerContext ctx, byte[] reply, long delayMillis, boolean closeAfter, Runnable afterWrite) {
        Channel channel = ctx.channel();
        BinaryLocalReplies replies = channel.hasAttr(LOCAL_REPLIES) ? channel.attr(LOCAL_REPLIES).get() : null;
        if ((replies == null || replies.waiting.isEmpty()) && delayMillis <= 0) {
            writeNow(ctx, reply, closeAfter, afterWrite);
            if (!channel.isWritable() && channel.isActive() && !BinaryRelay.hasRelay(channel)) {
                // created only now, so a client that reads its replies costs nothing more
                (replies != null ? replies : attach(ctx)).pauseOrResumeReads();
            }
            return;
        }
        if (!channel.isActive()) {
            return;
        }
        if (replies == null) {
            replies = attach(ctx);
        }
        long due = ctx.executor().ticker().nanoTime() + MILLISECONDS.toNanos(Math.max(0, delayMillis));
        replies.add(new Reply(reply, due, closeAfter, afterWrite));
    }

    /**
     * Stops or starts reading the connection as it stops or starts being writable, once it has had a reply written
     * by this class that left it not writable, or a reply waiting. Acts on what the channel says when it runs.
     */
    static void writabilityChanged(Channel channel) {
        BinaryLocalReplies replies = channel.hasAttr(LOCAL_REPLIES) ? channel.attr(LOCAL_REPLIES).get() : null;
        if (replies != null) {
            replies.pauseOrResumeReads();
        }
    }

    private static BinaryLocalReplies attach(ChannelHandlerContext ctx) {
        BinaryLocalReplies replies = new BinaryLocalReplies(ctx);
        ctx.channel().attr(LOCAL_REPLIES).set(replies);
        return replies;
    }

    /**
     * Whether a reply that closes the connection is waiting: what the client sends after it is not answered.
     */
    static boolean closing(Channel channel) {
        BinaryLocalReplies replies = channel.hasAttr(LOCAL_REPLIES) ? channel.attr(LOCAL_REPLIES).get() : null;
        return replies != null && replies.closing;
    }

    /**
     * Drops the replies still waiting and cancels their timer, once the connection has closed or its handler is gone.
     */
    static void discard(Channel channel) {
        BinaryLocalReplies replies = channel.hasAttr(LOCAL_REPLIES) ? channel.attr(LOCAL_REPLIES).getAndSet(null) : null;
        if (replies != null) {
            if (replies.timer != null) {
                replies.timer.cancel(false);
                replies.timer = null;
            }
            replies.waiting.clear();
            if (replies.readsPaused) {
                replies.readsPaused = false;
                ChannelReadPause.resume(channel);
            }
        }
    }

    private void add(Reply reply) {
        waiting.add(reply);
        closing |= reply.closeAfter;
        if (waiting.size() == 1) {
            writeDue();
        } else {
            pauseOrResumeReads();
        }
    }

    private void writeDue() {
        timer = null;
        Reply next;
        while ((next = waiting.peek()) != null) {
            long remaining = next.dueNanos - ctx.executor().ticker().nanoTime();
            if (remaining > 0) {
                timer = ctx.executor().schedule(this::writeDue, remaining, NANOSECONDS);
                return;
            }
            waiting.poll();
            writeNow(ctx, next.bytes, next.closeAfter, next.afterWrite);
            if (next.closeAfter) {
                waiting.clear();
            }
        }
        pauseOrResumeReads();
    }

    private void pauseOrResumeReads() {
        Channel channel = ctx.channel();
        boolean clientBehind = !channel.isWritable() && !BinaryRelay.hasRelay(channel);
        if (!readsPaused) {
            if (channel.isActive() && (waiting.size() > MAX_WAITING_REPLIES || clientBehind)) {
                readsPaused = true;
                ChannelReadPause.pause(channel);
            }
        } else if (waiting.isEmpty() && !clientBehind) {
            readsPaused = false;
            ChannelReadPause.resume(channel);
        }
    }

    private static void writeNow(ChannelHandlerContext ctx, byte[] reply, boolean closeAfter, Runnable afterWrite) {
        ctx.writeAndFlush(Unpooled.copiedBuffer(reply));
        if (afterWrite != null) {
            afterWrite.run();
        }
        if (closeAfter) {
            ctx.close();
        }
    }

    private static final class Reply {
        private final byte[] bytes;
        private final long dueNanos;
        private final boolean closeAfter;
        private final Runnable afterWrite;

        private Reply(byte[] bytes, long dueNanos, boolean closeAfter, Runnable afterWrite) {
            this.bytes = bytes;
            this.dueNanos = dueNanos;
            this.closeAfter = closeAfter;
            this.afterWrite = afterWrite;
        }
    }
}
