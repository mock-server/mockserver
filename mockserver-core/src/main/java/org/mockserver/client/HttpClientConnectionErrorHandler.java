package org.mockserver.client;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Message;

import java.util.concurrent.CompletableFuture;

import static org.mockserver.client.NettyHttpClient.RESPONSE_FUTURE;

@ChannelHandler.Sharable
public class HttpClientConnectionErrorHandler extends ChannelDuplexHandler {

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        CompletableFuture<? extends Message> responseFuture = ctx.channel().attr(RESPONSE_FUTURE).get();
        if (responseFuture != null && !responseFuture.isDone()) {
            responseFuture.completeExceptionally(new SocketConnectionException("Channel handler removed before valid response has been received"));
        }
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        CompletableFuture<? extends Message> responseFuture = ctx.channel().attr(RESPONSE_FUTURE).get();
        if (!responseFuture.isDone()) {
            responseFuture.completeExceptionally(cause);
        }
        super.exceptionCaught(ctx, cause);
    }
}
