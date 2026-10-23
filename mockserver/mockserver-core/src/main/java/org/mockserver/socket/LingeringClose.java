package org.mockserver.socket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.DuplexChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Ends a connection whose client may still be sending a request body the server will never read. Closing such a socket
 * with unread bytes makes the kernel send a reset instead of a FIN, and a client's stack may then discard the response
 * it has not read yet. So the output is ended (after a TLS {@code close_notify}), inbound bytes are discarded before any
 * decoder, and the socket is closed when the client closes or after {@link #LINGER_MILLIS}, whichever comes first.
 */
public final class LingeringClose {

    /**
     * How long a connection whose output has been ended waits for the other end to close before its socket is closed.
     */
    public static final long LINGER_MILLIS = 5_000;

    private LingeringClose() {
    }

    /**
     * Starts a lingering close; call it once the last response has been written, as nothing written after is sent.
     * A channel that is not a socket (an HTTP/2 stream, say) or is no longer active is closed at once. The connection is
     * {@link ConnectionClosing#mark marked} closing first, as the close happens beneath its pipelining gate.
     */
    public static void close(Channel channel) {
        ConnectionClosing.mark(channel);
        if (channel.eventLoop().inEventLoop()) {
            start(channel);
        } else {
            channel.eventLoop().execute(() -> start(channel));
        }
    }

    private static void start(Channel channel) {
        if (!channel.isActive() || !(channel instanceof DuplexChannel)) {
            channel.close();
            return;
        }
        if (channel.pipeline().get(DiscardInbound.class) != null) {
            return;
        }
        channel.pipeline().addFirst(DiscardInbound.INSTANCE);
        channel.config().setAutoRead(true);
        closeSocketUnlessClosedWithinLinger(channel);

        SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
        ChannelFuture outputWritten = sslHandler != null ? sslHandler.closeOutbound() : channel.newSucceededFuture();
        outputWritten.addListener(written -> {
            if (written.isSuccess() && channel.isActive()) {
                ReadAfterFailedWrite.endOutput((DuplexChannel) channel).addListener(shutdown -> {
                    if (!shutdown.isSuccess()) {
                        closeSocket(channel);
                    }
                });
            } else {
                closeSocket(channel);
            }
        });
    }

    /**
     * Closes the socket after {@link #LINGER_MILLIS} unless the channel has closed by then: the bound on how long a
     * client that never closes can hold a connection whose output has been ended.
     */
    public static void closeSocketUnlessClosedWithinLinger(Channel channel) {
        ScheduledFuture<?> limit = channel.eventLoop().schedule(() -> closeSocket(channel), LINGER_MILLIS, MILLISECONDS);
        channel.closeFuture().addListener(closed -> limit.cancel(false));
    }

    /**
     * Closes the channel at its socket, not through its pipeline: through the pipeline a TLS handler would first try
     * to write its {@code close_notify} (to an output that may have ended), and an HTTP/2 handler would wait for its
     * streams. Closing the socket fails what is queued and fires {@code channelInactive}.
     */
    public static void closeSocket(Channel channel) {
        channel.unsafe().close(channel.unsafe().voidPromise());
    }

    @ChannelHandler.Sharable
    static final class DiscardInbound extends ChannelInboundHandlerAdapter {

        static final DiscardInbound INSTANCE = new DiscardInbound();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ReferenceCountUtil.release(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            // nothing reaches the handlers behind, so neither does the end of a read
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof ChannelInputShutdownEvent) {
                closeSocket(ctx.channel());
            } else {
                ctx.fireUserEventTriggered(evt);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // typically the client resetting a connection it no longer needs
            closeSocket(ctx.channel());
        }
    }
}
