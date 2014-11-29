package org.mockserver.integration.testserver;

import com.google.common.base.Charsets;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class TestServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {

        FullHttpResponse response1;
        if (request.getUri().equals("/unknown")) {
            response1 = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND);
        } else if (request.getUri().equals("/test_headers_and_body")) {
            response1 = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer("an_example_body".getBytes(Charsets.UTF_8)));
            response1.headers().set("X-Test", "test_headers_and_body");
            response1.headers().set(CONTENT_TYPE, "text/plain");
            response1.headers().set(CONTENT_LENGTH, response1.content().readableBytes());
        } else if (request.getUri().equals("/test_headers_only")) {
            response1 = new DefaultFullHttpResponse(HTTP_1_1, OK);
            response1.headers().set("X-Test", "test_headers_only");
            response1.headers().set(CONTENT_TYPE, "text/plain");
            response1.headers().set(CONTENT_LENGTH, response1.content().readableBytes());
        } else if (request.getUri().endsWith("/echo")) {
            // echo back body
            response1 = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.copiedBuffer(request.content()));
            // echo back headers
            List<String> headerNames = new ArrayList<String>(request.headers().names());
            Collections.sort(headerNames);
            for (String headerName : headerNames) {
                response1.headers().set(headerName.toLowerCase(), request.headers().get(headerName));
            }
            response1.headers().set(CONTENT_LENGTH, response1.content().readableBytes());
        } else {
            response1 = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer("Hello World".getBytes(Charsets.UTF_8)));
            response1.headers().set(CONTENT_TYPE, "text/plain");
            response1.headers().set(CONTENT_LENGTH, response1.content().readableBytes());
        }
        FullHttpResponse response = response1;

        response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
        if (isKeepAlive(request)) {
            response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }
        ctx.write(response);
        ctx.flush();
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
