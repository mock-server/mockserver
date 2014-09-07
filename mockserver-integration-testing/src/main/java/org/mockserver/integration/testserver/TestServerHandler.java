/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.mockserver.integration.testserver;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class TestServerHandler extends SimpleChannelInboundHandler<Object> {

    // requests
    private DefaultFullHttpRequest mockServerHttpRequest = null;
    private ByteBuf responseContent;
    private HttpRequest request = null;

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpObject && ((HttpObject) msg).getDecoderResult().isSuccess()) {
            if (msg instanceof HttpRequest) {
                request = (HttpRequest) msg;
            }

            if (msg instanceof HttpContent) {
                ByteBuf requestContent = ((HttpContent) msg).content();

                if (requestContent.isReadable()) {
                    if (this.responseContent == null) {
                        this.responseContent = Unpooled.copiedBuffer(requestContent);
                    } else {
                        this.responseContent.writeBytes(requestContent);
                    }
                }

                if (msg instanceof LastHttpContent) {
                    mockServerHttpRequest = new DefaultFullHttpRequest(request.getProtocolVersion(), request.getMethod(), request.getUri(), requestContent);
                    mockServerHttpRequest.headers().add(request.headers());

                    LastHttpContent trailer = (LastHttpContent) msg;
                    if (!trailer.trailingHeaders().isEmpty()) {
                        mockServerHttpRequest.headers().entries().addAll(trailer.trailingHeaders().entries());
                    }

                    writeResponse(ctx, handleRequest(mockServerHttpRequest), isKeepAlive(request), is100ContinueExpected(request));
                }

            }
        } else {
            ctx.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
        }
    }

    private void writeResponse(ChannelHandlerContext ctx, FullHttpResponse response, boolean isKeepAlive, boolean is100ContinueExpected) {
        if (isKeepAlive) {
            // Add 'Content-Length' header only for a keep-alive connection.
            response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
            // Add keep alive header as per:
            // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
            response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }
        if (is100ContinueExpected) {
            ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
        }
        ctx.write(response);
        ctx.flush();
    }


    public FullHttpResponse handleRequest(DefaultFullHttpRequest req) {
        FullHttpResponse response;
        if (req.getUri().equals("/unknown")) {
            response = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND);
        } else if (req.getUri().equals("/test_headers_and_body")) {
            response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer("an_example_body".getBytes(Charsets.UTF_8)));
            response.headers().set("X-Test", "test_headers_and_body");
            response.headers().set(CONTENT_TYPE, "text/plain");
            response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
        } else if (req.getUri().equals("/test_headers_only")) {
            response = new DefaultFullHttpResponse(HTTP_1_1, OK);
            response.headers().set("X-Test", "test_headers_only");
            response.headers().set(CONTENT_TYPE, "text/plain");
            response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
        } else if (req.getUri().equals("/echo")) {
            // echo back body
            response = new DefaultFullHttpResponse(HTTP_1_1, OK, req.content());
            // echo back headers
            List<String> headerNames = new ArrayList<String>(req.headers().names());
            Collections.sort(headerNames);
            for (String headerName : headerNames) {
                response.headers().set(headerName.toLowerCase(), req.headers().get(headerName));
            }
            response.headers().set(CONTENT_LENGTH, response.content().readableBytes());

        } else {
            response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer("Hello World".getBytes(Charsets.UTF_8)));
            response.headers().set(CONTENT_TYPE, "text/plain");
            response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
        }
        return response;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
