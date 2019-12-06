package org.mockserver.client;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.mockserver.model.HttpResponse;

import java.util.concurrent.CompletableFuture;

import static org.mockserver.client.NettyHttpClient.RESPONSE_FUTURE;

@ChannelHandler.Sharable
public class HttpClientConnectionHandler extends ChannelDuplexHandler {

    private void updatePromise(ChannelHandlerContext ctx, String action) {
        CompletableFuture<HttpResponse> responseFuture = ctx.channel().attr(RESPONSE_FUTURE).get();
        if (responseFuture != null && !responseFuture.isDone()) {
            responseFuture.completeExceptionally(new SocketConnectionException("Channel " + action + " before valid response has been received"));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        updatePromise(ctx, "set as inactive");
        super.channelInactive(ctx);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        updatePromise(ctx, "disconnected");
        super.disconnect(ctx, promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        updatePromise(ctx, "closed");
        super.close(ctx, promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        updatePromise(ctx, "deregistered");
        super.deregister(ctx, promise);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        updatePromise(ctx, "unregistered");
        super.channelUnregistered(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        updatePromise(ctx, "handler removed");
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        CompletableFuture<HttpResponse> responseFuture = ctx.channel().attr(RESPONSE_FUTURE).get();
        if (!responseFuture.isDone()) {
            responseFuture.completeExceptionally(cause);
        }
        super.exceptionCaught(ctx, cause);
    }
}
