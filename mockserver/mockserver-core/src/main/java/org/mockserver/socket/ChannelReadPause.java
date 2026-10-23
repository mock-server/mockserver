package org.mockserver.socket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

/**
 * Reference-counted pausing of a channel's reads. Several independent handlers can want the same channel's
 * reads paused at once (a parked inbound breakpoint frame, pending WebSocket replies, a TCP chaos latency
 * queue, relay backpressure, a connection delay); toggling {@code autoRead} directly would let the first one
 * to finish turn reading back on while another still needs it off. Auto-read is turned off by the first
 * {@link #pause} and back on only by the {@link #resume} that balances the last one, which always issues the
 * next read, even when something else has already turned auto-read back on. Every holder on a channel must use
 * this class rather than {@code config().setAutoRead}.
 * <p>
 * Turning auto-read off does not stop a handler from requesting a read itself: a decoder that saw no complete
 * message, or an {@code HttpContentDecoder} left in a WebSocket pipeline, calls {@code ctx.read()} after every
 * read when auto-read is off, which would keep the socket draining. So the first pause also installs a gate at
 * the head of the pipeline that drops read requests while any hold remains.
 */
public final class ChannelReadPause {

    private static final AttributeKey<int[]> HOLDS = AttributeKey.valueOf("mockserver.channelReadPauseHolds");
    static final String GATE_NAME = "mockserverReadPauseGate";

    private ChannelReadPause() {
        // utility class
    }

    /**
     * Add one hold on the channel's reads; the channel stops reading until every hold is released.
     */
    public static void pause(Channel channel) {
        onEventLoop(channel, () -> {
            Attribute<int[]> attribute = channel.attr(HOLDS);
            int[] holds = attribute.get();
            if (holds == null) {
                holds = new int[1];
                attribute.set(holds);
            }
            if (holds[0]++ == 0) {
                ChannelPipeline pipeline = channel.pipeline();
                if (pipeline.get(GATE_NAME) == null) {
                    pipeline.addFirst(GATE_NAME, ReadGate.INSTANCE);
                }
                channel.config().setAutoRead(false);
            }
        });
    }

    /**
     * Release one hold added by {@link #pause}; releasing the last one resumes reading. A release without a
     * matching hold is ignored.
     */
    public static void resume(Channel channel) {
        if (channel.eventLoop().inEventLoop()) {
            releaseHold(channel);
        } else {
            channel.eventLoop().execute(() -> releaseHold(channel));
        }
    }

    private static void releaseHold(Channel channel) {
        int[] holds = channel.attr(HOLDS).get();
        if (holds != null && holds[0] > 0 && --holds[0] == 0) {
            if (channel.config().isAutoRead()) {
                // something (a lingering close, a connection reset) turned auto-read on under a hold, and
                // the gate dropped the read that triggered, so nothing would read the socket again
                channel.read();
            } else {
                channel.config().setAutoRead(true);
            }
        }
    }

    /**
     * Holds currently on the channel's reads (read this on the channel's event loop).
     */
    public static int holds(Channel channel) {
        int[] holds = channel.hasAttr(HOLDS) ? channel.attr(HOLDS).get() : null;
        return holds != null ? holds[0] : 0;
    }

    private static void onEventLoop(Channel channel, Runnable action) {
        EventLoop eventLoop = channel.eventLoop();
        if (eventLoop.inEventLoop()) {
            action.run();
        } else {
            eventLoop.execute(action);
        }
    }

    @ChannelHandler.Sharable
    private static final class ReadGate extends ChannelOutboundHandlerAdapter {
        private static final ReadGate INSTANCE = new ReadGate();

        @Override
        public void read(ChannelHandlerContext ctx) {
            if (holds(ctx.channel()) == 0) {
                ctx.read();
            }
        }
    }
}
