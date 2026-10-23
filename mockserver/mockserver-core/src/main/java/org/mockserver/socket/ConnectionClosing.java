package org.mockserver.socket;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.AttributeKey;

/**
 * Records that a connection will close, for a close its HTTP/1.1 pipelining gate cannot otherwise see in time: one
 * decided off the event loop after the response is written, one that is delayed, or one issued beneath the gate (a
 * lingering close, a reset). Call {@link #mark} before writing the response or ending the exchange; closes issued on
 * the event loop through the pipeline, and responses that say {@code Connection: close}, need no mark.
 */
public final class ConnectionClosing {

    private static final AttributeKey<Boolean> CLOSING = AttributeKey.valueOf("mockserver.connectionClosing");

    private ConnectionClosing() {
    }

    /**
     * Marks the connection as closing and, the first time, fires {@link ConnectionClosingEvent} from the head of its
     * pipeline on its event loop (at once when called there). The mark is visible to the event loop as soon as this
     * returns, so a write issued after it from any thread is seen after it.
     */
    public static void mark(Channel channel) {
        if (channel.attr(CLOSING).setIfAbsent(Boolean.TRUE) != null || !channel.isRegistered()) {
            return;
        }
        EventLoop eventLoop = channel.eventLoop();
        if (eventLoop.inEventLoop()) {
            channel.pipeline().fireUserEventTriggered(ConnectionClosingEvent.INSTANCE);
        } else {
            eventLoop.execute(() -> channel.pipeline().fireUserEventTriggered(ConnectionClosingEvent.INSTANCE));
        }
    }

    /**
     * Whether {@link #mark} has been called on the connection. Never creates the attribute, so it never allocates.
     */
    public static boolean isMarked(Channel channel) {
        return channel.hasAttr(CLOSING) && Boolean.TRUE.equals(channel.attr(CLOSING).get());
    }
}
