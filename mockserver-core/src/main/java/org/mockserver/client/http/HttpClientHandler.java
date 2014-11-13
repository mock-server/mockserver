package org.mockserver.client.http;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.CharsetUtil;
import org.mockserver.model.HttpResponse;

public class HttpClientHandler extends SimpleChannelInboundHandler<HttpResponse> {

    private final SettableFuture<HttpResponse> responseFuture = SettableFuture.<HttpResponse>create();

    public SettableFuture<HttpResponse> getResponseFuture() {
        return responseFuture;
    }

    public HttpClientHandler() {
        super(false);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpResponse response) {
        responseFuture.set(response);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        responseFuture.setException(cause);
        ctx.close();
    }
}