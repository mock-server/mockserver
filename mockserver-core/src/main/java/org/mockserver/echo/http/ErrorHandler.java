package org.mockserver.echo.http;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class ErrorHandler extends ChannelDuplexHandler {

    private final EchoServer.Error error;

    public ErrorHandler(EchoServer.Error error) {
        this.error = error;
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        if (error == EchoServer.Error.CLOSE_CONNECTION) {
            ctx.channel().disconnect();
            ctx.channel().close();
        } else {
            ctx.read();
        }
    }

}
