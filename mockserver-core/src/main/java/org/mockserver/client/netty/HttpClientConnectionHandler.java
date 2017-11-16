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

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (!responseFuture.isDone()) {
            responseFuture.setException(new RuntimeException("Channel set as inactive before valid response has been received"));
        }
        super.channelInactive(ctx);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (!responseFuture.isDone()) {
            responseFuture.setException(new RuntimeException("Channel disconnected before valid response has been received"));
        }
        super.disconnect(ctx, promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (!responseFuture.isDone()) {
            responseFuture.setException(new RuntimeException("Channel closed before valid response has been received"));
        }
        super.close(ctx, promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (!responseFuture.isDone()) {
            responseFuture.setException(new RuntimeException("Channel deregistered before valid response has been received"));
        }
        super.deregister(ctx, promise);
    }
}