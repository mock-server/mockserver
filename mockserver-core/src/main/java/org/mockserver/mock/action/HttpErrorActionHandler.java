package org.mockserver.mock.action;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpServerCodec;
import org.mockserver.model.HttpError;

/**
 * @author jamesdbloom
 */
public class HttpErrorActionHandler {

    public void handle(HttpError httpError, ChannelHandlerContext ctx) {
        if (httpError.getResponseBytes() != null) {
            // write byte directly by skipping over HTTP codec
            ChannelHandlerContext httpCodecContext = ctx.pipeline().context(HttpServerCodec.class);
            if (httpCodecContext != null) {
                httpCodecContext.writeAndFlush(Unpooled.wrappedBuffer(httpError.getResponseBytes())).awaitUninterruptibly();
            }
        }
        if (httpError.getDropConnection() != null && httpError.getDropConnection()) {
            ctx.disconnect();
            ctx.close();
        }
    }

}
