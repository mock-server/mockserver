package org.mockserver.client.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.NotSslRecordException;
import org.mockserver.model.HttpResponse;

import javax.net.ssl.SSLException;

import static org.mockserver.client.netty.NettyHttpClient.CHANNEL_POOL;
import static org.mockserver.client.netty.NettyHttpClient.RESPONSE_FUTURE;

@ChannelHandler.Sharable
public class HttpClientHandler extends SimpleChannelInboundHandler<HttpResponse> {

    public HttpClientHandler() {
        super(false);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpResponse response) {
        ctx.channel().attr(RESPONSE_FUTURE).get().set(response);
        releaseChannelToPool(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (isNotSslException(cause)) {
            cause.printStackTrace();
        }
        ctx.channel().attr(RESPONSE_FUTURE).get().setException(cause);
        releaseChannelToPool(ctx.channel());

    }

    private void releaseChannelToPool(Channel channel) {
        channel.attr(CHANNEL_POOL).get().release(channel);
    }

    private boolean isNotSslException(Throwable cause) {
        return !(cause.getCause() instanceof SSLException || cause instanceof DecoderException | cause instanceof NotSslRecordException);
    }
}
