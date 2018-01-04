package org.mockserver.proxy.relay;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;

import static org.mockserver.exception.ExceptionHandler.closeOnFlush;
import static org.mockserver.exception.ExceptionHandler.shouldNotIgnoreException;
import static org.mockserver.socket.NettySslContextFactory.nettySslContextFactory;
import static org.mockserver.unification.PortUnificationHandler.isSslEnabledDownstream;

public class UpstreamProxyRelayHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final Logger logger;
    private volatile Channel upstreamChannel;
    private volatile Channel downstreamChannel;

    public UpstreamProxyRelayHandler(Channel upstreamChannel, Channel downstreamChannel, Logger logger) {
        super(false);
        this.upstreamChannel = upstreamChannel;
        this.downstreamChannel = downstreamChannel;
        this.logger = logger;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.read();
        ctx.write(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest request) {
        if (isSslEnabledDownstream(upstreamChannel) && downstreamChannel.pipeline().get(SslHandler.class) == null) {
            downstreamChannel.pipeline().addFirst(nettySslContextFactory().createClientSslContext().newHandler(ctx.alloc()));
        }
        downstreamChannel.writeAndFlush(request).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    ctx.channel().read();
                } else {
                    if (isNotSocketClosedException(future.cause())) {
                        logger.error("Exception while returning response for request \"" + request.method() + " " + request.uri() + "\"", future.cause());
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
        closeOnFlush(downstreamChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (shouldNotIgnoreException(cause)) {
            logger.warn("Exception caught by upstream relay handler -> closing pipeline " + ctx.channel(), cause);
        }
        closeOnFlush(ctx.channel());
    }

}
