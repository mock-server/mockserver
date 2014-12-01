package org.mockserver.proxy.relay;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.slf4j.Logger;

public abstract class ProxyRelayHandler<T> extends SimpleChannelInboundHandler<T> {

    private final Logger logger;
    private volatile Channel inboundChannel;

    public ProxyRelayHandler(Channel inboundChannel, Logger logger) {
        super(false);
        this.inboundChannel = inboundChannel;
        this.logger = logger;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.read();
        ctx.write(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final T msg) {
        inboundChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    ctx.channel().read();
                } else {
                    logger.error("Exception while returning response message", future.cause());
                    future.channel().close();
                }
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception while reading from channel", cause);
        closeOnFlush(ctx.channel());
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    public static void closeOnFlush(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

}
