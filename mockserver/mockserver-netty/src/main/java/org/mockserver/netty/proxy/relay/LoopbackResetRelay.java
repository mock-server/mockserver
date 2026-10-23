package org.mockserver.netty.proxy.relay;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import org.mockserver.socket.ConnectionReset;

/**
 * Carries a deliberate reset (an {@code HttpError} {@code resetConnection}, or a chaos {@code resetPeer} or
 * {@code resetMidResponse}) of a CONNECT/SOCKS tunnel's loopback through to the tunnel's client connection, which the
 * relay would otherwise end with a clean close (or, on h2, a GOAWAY and stream resets). MockServer's end of the
 * loopback marks the client connection, then ends the loopback's output rather than resetting it
 * ({@link ConnectionReset#reset}); this handler, first on the relay's end of the loopback, resets the client
 * connection as the loopback closes (or a reset of it is read), before the relay's other handlers react to it, and
 * after what the relay has already written to the client, so a response MockServer wrote first reaches the client
 * first. A loopback reset nobody asked for is left to the relay's usual handling.
 */
public final class LoopbackResetRelay extends ChannelInboundHandlerAdapter {

    private static final AttributeKey<Boolean> RESET_REQUESTED = AttributeKey.valueOf("MOCKSERVER_RELAY_RESET_REQUESTED");

    private final Channel proxyClientChannel;

    LoopbackResetRelay(Channel proxyClientChannel) {
        this.proxyClientChannel = proxyClientChannel;
    }

    /**
     * On MockServer's end of a relay loopback: a reset of it also resets the tunnel's client connection.
     */
    public static void relayResetsOf(Channel accepted) {
        Channel proxyClient = RelayLoopbackAddresses.proxyClientOf(accepted);
        if (proxyClient != null) {
            // not reset here: the relay has yet to pass on what MockServer wrote to the loopback before its reset
            accepted.attr(ConnectionReset.ON_RESET).set(() -> proxyClient.attr(RESET_REQUESTED).set(Boolean.TRUE));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (ConnectionReset.isPeerReset(cause)) {
            resetProxyClientIfRequested();
        }
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        resetProxyClientIfRequested();
        ctx.fireChannelInactive();
    }

    private void resetProxyClientIfRequested() {
        if (proxyClientChannel.hasAttr(RESET_REQUESTED) && Boolean.TRUE.equals(proxyClientChannel.attr(RESET_REQUESTED).getAndSet(null))) {
            // written beneath any TLS or HTTP/2 handler, so it completes once what is already queued has been written
            ChannelHandlerContext first = proxyClientChannel.pipeline().firstContext();
            ChannelFuture relayed = first != null ? first.writeAndFlush(Unpooled.EMPTY_BUFFER) : proxyClientChannel.newSucceededFuture();
            relayed.addListener(written -> ConnectionReset.reset(proxyClientChannel));
        }
    }
}
