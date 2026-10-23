package org.mockserver.socket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.util.AttributeKey;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import java.io.IOException;
import java.util.Locale;

/**
 * Resets a TCP connection (an RST rather than a clean close), and recognises a connection its peer reset.
 */
public final class ConnectionReset {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(ConnectionReset.class);

    /**
     * Run as a connection is {@link #reset}, before the reset: MockServer's own end of a CONNECT/SOCKS tunnel's
     * loopback uses it to reset the tunnel's client connection too.
     */
    public static final AttributeKey<Runnable> ON_RESET = AttributeKey.valueOf("MOCKSERVER_CONNECTION_ON_RESET");

    private ConnectionReset() {
    }

    /**
     * Close the connection with an RST: {@code SO_LINGER} 0 makes the close abortive. A stream child of an HTTP/2
     * multiplex connection closes as a RST_STREAM only, so its parent connection is reset instead. The close starts
     * at the head of the pipeline, below any {@code SslHandler}, so no TLS {@code close_notify} is sent first, as
     * none is when a peer resets.
     */
    public static ChannelFuture reset(Channel channel) {
        Channel connection = channel instanceof Http2StreamChannel && channel.parent() != null ? channel.parent() : channel;
        Runnable onReset = connection.attr(ON_RESET).get();
        if (onReset != null) {
            onReset.run();
        }
        boolean abortive;
        try {
            abortive = connection.config().setOption(ChannelOption.SO_LINGER, 0);
        } catch (RuntimeException alreadyClosed) {
            abortive = !connection.isOpen();
        }
        if (!abortive && MockServerLogger.isEnabled(Level.WARN)) {
            MOCK_SERVER_LOGGER.logEvent(new LogEntry()
                .setLogLevel(Level.WARN)
                .setMessageFormat("unable to set SO_LINGER 0 on{}so it closes without a TCP RST")
                .setArguments(connection));
        }
        ChannelHandlerContext first = connection.pipeline().firstContext();
        return first != null ? first.close() : connection.close();
    }

    /**
     * Whether a read failed because the peer reset the connection (ECONNRESET), as opposed to closing it.
     */
    public static boolean isPeerReset(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof IOException && cause.getMessage() != null && cause.getMessage().toLowerCase(Locale.ROOT).contains("connection reset")) {
                return true;
            }
        }
        return false;
    }
}
