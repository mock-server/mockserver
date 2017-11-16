package org.mockserver.client.netty;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.mockserver.model.HttpResponse;

public class HttpClientConnectionHandler extends ChannelDuplexHandler {

    private final SettableFuture<HttpResponse> responseFuture;

    public HttpClientConnectionHandler(SettableFuture<HttpResponse> responseFuture) {
        this.responseFuture = responseFuture;
    }

    private void updatePromise(String action) {
        if (!responseFuture.isDone()) {
            responseFuture.setException(new RuntimeException("Channel " + action + " before valid response has been received"));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        updatePromise("set as inactive");
        super.channelInactive(ctx);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        updatePromise("disconnected");
        super.disconnect(ctx, promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        updatePromise("closed");
        super.close(ctx, promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        updatePromise("deregistered");
        super.deregister(ctx, promise);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        updatePromise("unregistered");
        super.channelUnregistered(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        updatePromise("handler removed");
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!responseFuture.isDone()) {
            responseFuture.setException(new RuntimeException("Exception caught before valid response has been received", cause));
        }
        super.exceptionCaught(ctx, cause);
    }
}