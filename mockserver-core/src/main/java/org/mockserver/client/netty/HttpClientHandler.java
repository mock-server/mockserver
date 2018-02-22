package org.mockserver.client.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.NotSslRecordException;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;

import javax.net.ssl.SSLException;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.client.netty.NettyHttpClient.*;
import static org.mockserver.log.model.MessageLogEntry.LogMessageType.FORWARDED_REQUEST;

@ChannelHandler.Sharable
public class HttpClientHandler extends SimpleChannelInboundHandler<HttpResponse> {

    private final MockServerLogger mockServerLogger;

    public HttpClientHandler(MockServerLogger mockServerLogger) {
        super(false);
        this.mockServerLogger = mockServerLogger;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpResponse response) {
        ctx.channel().attr(RESPONSE_FUTURE).get().set(response);
        ctx.close();

        if (!response.containsHeader("x-forwarded-by", "MockServer")) {
            mockServerLogger.debug(FORWARDED_REQUEST, "Sent request to: {}" + NEW_LINE + "request: {}" + NEW_LINE + " and received response: {}",
                ctx.channel().attr(REMOTE_SOCKET).get(),
                ctx.channel().attr(REQUEST).get(),
                response);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (isNotSslException(cause)) {
            cause.printStackTrace();
        }
        ctx.channel().attr(RESPONSE_FUTURE).get().setException(cause);
        ctx.close();
    }

    private boolean isNotSslException(Throwable cause) {
        return !(cause.getCause() instanceof SSLException || cause instanceof DecoderException | cause instanceof NotSslRecordException);
    }
}
