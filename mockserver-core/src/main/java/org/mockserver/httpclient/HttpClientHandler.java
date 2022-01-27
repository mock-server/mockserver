package org.mockserver.httpclient;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.NotSslRecordException;
import org.mockserver.model.Message;

import javax.net.ssl.SSLException;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.httpclient.NettyHttpClient.RESPONSE_FUTURE;

@ChannelHandler.Sharable
public class HttpClientHandler extends SimpleChannelInboundHandler<Message> {

    private final List<String> connectionClosedStrings = Arrays.asList(
        "Broken pipe",
        "(broken pipe)",
        "Connection reset"
    );

    HttpClientHandler() {
        super(false);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Message response) {
        ctx.channel().attr(RESPONSE_FUTURE).get().complete(response);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (isNotSslException(cause) && isNotConnectionReset(cause)) {
            cause.printStackTrace();
        }
        ctx.channel().attr(RESPONSE_FUTURE).get().completeExceptionally(cause);
        ctx.close();
    }

    private boolean isNotSslException(Throwable cause) {
        return !(cause.getCause() instanceof SSLException || cause instanceof DecoderException | cause instanceof NotSslRecordException);
    }

    private boolean isNotConnectionReset(Throwable cause) {
        return connectionClosedStrings.stream().noneMatch(connectionClosedString ->
            (isNotBlank(cause.getMessage()) && cause.getMessage().contains(connectionClosedString))
                || (cause.getCause() != null && isNotBlank(cause.getCause().getMessage()) && cause.getCause().getMessage().contains(connectionClosedString)));
    }
}
