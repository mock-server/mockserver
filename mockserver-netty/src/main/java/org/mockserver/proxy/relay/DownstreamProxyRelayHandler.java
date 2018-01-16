package org.mockserver.proxy.relay;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import org.mockserver.logging.MockServerLogger;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;

import static org.mockserver.exception.ExceptionHandler.closeOnFlush;
import static org.mockserver.exception.ExceptionHandler.shouldNotIgnoreException;

public class DownstreamProxyRelayHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    private final MockServerLogger mockServerLogger;
    private volatile Channel upstreamChannel;

    public DownstreamProxyRelayHandler(MockServerLogger mockServerLogger, Channel upstreamChannel) {
        super(false);
        this.upstreamChannel = upstreamChannel;
        this.mockServerLogger = mockServerLogger;
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
                        mockServerLogger.error("Exception while returning writing " + response, future.cause());
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
        if (shouldNotIgnoreException(cause)) {
            mockServerLogger.error("Exception caught by downstream relay handler -> closing pipeline " + ctx.channel(), cause);
        }
        closeOnFlush(ctx.channel());
    }

}
