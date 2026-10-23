package org.mockserver.netty.proxy.relay;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import org.mockserver.socket.ConnectionReset;

/**
 * Carries a deliberate reset (an {@code HttpError} {@code resetConnection}) of a CONNECT/SOCKS tunnel's loopback
 * through to the tunnel's client connection, which the relay would otherwise end with a clean close (or, on h2, a
 * GOAWAY and stream resets). MockServer's end of the loopback marks the client connection and resets it before it
 * resets the loopback; this handler, first on the relay's end of the loopback, resets the client connection again
 * as the loopback's reset is read, before the relay's other handlers react to it. A loopback reset nobody asked for
 * is left to the relay's usual handling.
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
            accepted.attr(ConnectionReset.ON_RESET).set(() -> {
                proxyClient.attr(RESET_REQUESTED).set(Boolean.TRUE);
                ConnectionReset.reset(proxyClient);
            });
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (Boolean.TRUE.equals(proxyClientChannel.attr(RESET_REQUESTED).get()) && ConnectionReset.isPeerReset(cause)) {
            ConnectionReset.reset(proxyClientChannel);
        }
        ctx.fireExceptionCaught(cause);
    }
}
