package org.mockserver.echo.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class EchoServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    protected void channelRead0(ChannelHandlerContext ctx11, FullHttpRequest request) {
        HttpResponseStatus responseStatus = OK;
        if (request.getUri().equals("/not_found")) {
            responseStatus = NOT_FOUND;
        }
        // echo back request headers and body
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, responseStatus, Unpooled.copiedBuffer(request.content()));
        response.headers().add(request.headers());

        // set hop-by-hop headers
        response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
        if (isKeepAlive(request)) {
            response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }
        if (is100ContinueExpected(request)) {
            ctx11.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
        }

        // write and flush
        ctx11.writeAndFlush(response);
    }

    public void exceptionCaught(ChannelHandlerContext ctx11, Throwable cause) {
        cause.printStackTrace();
        ctx11.close();
    }
}