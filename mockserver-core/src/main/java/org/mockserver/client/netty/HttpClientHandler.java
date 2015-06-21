package org.mockserver.client.netty;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.NotSslRecordException;
import org.mockserver.model.HttpResponse;

import javax.net.ssl.SSLException;

public class HttpClientHandler extends SimpleChannelInboundHandler<HttpResponse> {

    private final SettableFuture<HttpResponse> responseFuture = SettableFuture.<HttpResponse>create();

    public HttpClientHandler() {
        super(false);
    }

    public SettableFuture<HttpResponse> getResponseFuture() {
        return responseFuture;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpResponse response) {
        responseFuture.set(response);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (isNotSslException(cause)) {
            cause.printStackTrace();
        }
        responseFuture.setException(cause);
        ctx.close();
    }

    private boolean isNotSslException(Throwable cause) {
        return !(cause.getCause() instanceof SSLException || cause instanceof DecoderException | cause instanceof NotSslRecordException);
    }
}