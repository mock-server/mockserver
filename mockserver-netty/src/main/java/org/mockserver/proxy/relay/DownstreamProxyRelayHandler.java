package org.mockserver.proxy.relay;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import org.slf4j.Logger;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;

import static org.mockserver.proxy.error.ExceptionHandler.closeOnFlush;
import static org.mockserver.proxy.error.ExceptionHandler.shouldIgnoreException;

public class DownstreamProxyRelayHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    private final Logger logger;
    private volatile Channel upstreamChannel;

    public DownstreamProxyRelayHandler(Channel upstreamChannel, Logger logger) {
        super(false);
        this.upstreamChannel = upstreamChannel;
        this.logger = logger;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.read();
        ctx.write(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) {
        upstreamChannel.writeAndFlush(response).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    ctx.channel().read();
                } else {
                    if (isNotSocketClosedException(future.cause())) {
                        logger.error("Exception while returning writing " + response, future.cause());
                    }
                    future.channel().close();
                }
            }
        });
    }

    private boolean isNotSocketClosedException(Throwable cause) {
        return !(cause instanceof ClosedChannelException || cause instanceof ClosedSelectorException);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closeOnFlush(upstreamChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!shouldIgnoreException(cause)) {
            logger.warn("Exception caught by downstream relay handler -> closing pipeline " + ctx.channel(), cause);
        }
        closeOnFlush(ctx.channel());
    }

}
