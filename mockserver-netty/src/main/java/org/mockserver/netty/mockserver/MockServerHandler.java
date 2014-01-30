/*
 * Copyright 2012 The Netty Project
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
package org.mockserver.netty.mockserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mappers.MockServerToNettyResponseMapper;
import org.mockserver.mappers.NettyToMockServerRequestMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServer;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.NettyHttpRequest;
import org.mockserver.proxy.filters.LogFilter;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;

public class MockServerHandler extends SimpleChannelInboundHandler<Object> {

    // mockserver
    private final MockServer mockServer;
    private final LogFilter logFilter;
    // netty
    private final boolean secure;
    private final NettyMockServer server;
    // mappers
    private NettyToMockServerRequestMapper nettyToMockServerRequestMapper = new NettyToMockServerRequestMapper();
    private MockServerToNettyResponseMapper mockServerToNettyResponseMapper = new MockServerToNettyResponseMapper();
    // serializers
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    // requests
    private NettyHttpRequest mockServerHttpRequest = null;
    private HttpRequest request = null;

    public MockServerHandler(MockServer mockServer, LogFilter logFilter, NettyMockServer server, boolean secure) {
        this.mockServer = mockServer;
        this.logFilter = logFilter;
        this.server = server;
        this.secure = secure;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpObject && ((HttpObject) msg).getDecoderResult().isSuccess()) {
            if (msg instanceof HttpRequest) {
                request = (HttpRequest) msg;
                mockServerHttpRequest = new NettyHttpRequest(request.getProtocolVersion(), request.getMethod(), request.getUri(), secure);
                mockServerHttpRequest.headers().add(request.headers());
            }

            if (msg instanceof HttpContent && mockServerHttpRequest != null) {
                ByteBuf content = ((HttpContent) msg).content();

                if (content.isReadable()) {
                    mockServerHttpRequest.content(content);
                }

                if (msg instanceof LastHttpContent) {

                    LastHttpContent trailer = (LastHttpContent) msg;
                    if (!trailer.trailingHeaders().isEmpty()) {
                        mockServerHttpRequest.headers().entries().addAll(trailer.trailingHeaders().entries());
                    }

                    writeResponse(ctx, mockResponse(mockServerHttpRequest), isKeepAlive(request));
                }

            }
        } else {
            ctx.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
        }
    }

    private void writeResponse(ChannelHandlerContext ctx, FullHttpResponse response, boolean isKeepAlive) {
        if (isKeepAlive) {
            // Add 'Content-Length' header only for a keep-alive connection.
            response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
            // Add keep alive header as per:
            // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
            response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }
        ctx.write(response);
    }

    private FullHttpResponse mockResponse(NettyHttpRequest nettyHttpRequest) {

        if (nettyHttpRequest.matches(HttpMethod.PUT, "/stop")) {

            server.stop();
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);

        } else if (nettyHttpRequest.matches(HttpMethod.PUT, "/dumpToLog")) {

            mockServer.dumpToLog(httpRequestSerializer.deserialize(nettyHttpRequest.content().toString(CharsetUtil.UTF_8)));
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);

        } else if (nettyHttpRequest.matches(HttpMethod.PUT, "/reset")) {

            logFilter.reset();
            mockServer.reset();
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);

        } else if (nettyHttpRequest.matches(HttpMethod.PUT, "/clear")) {

            org.mockserver.model.HttpRequest httpRequest = httpRequestSerializer.deserialize(nettyHttpRequest.content().toString(CharsetUtil.UTF_8));
            logFilter.clear(httpRequest);
            mockServer.clear(httpRequest);
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);

        } else if (nettyHttpRequest.matches(HttpMethod.PUT, "/expectation")) {

            Expectation expectation = expectationSerializer.deserialize(nettyHttpRequest.content().toString(CharsetUtil.UTF_8));
            mockServer.when(expectation.getHttpRequest(), expectation.getTimes()).thenRespond(expectation.getHttpResponse());
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CREATED);

        } else if (nettyHttpRequest.matches(HttpMethod.PUT, "/retrieve")) {

            Expectation[] expectations = logFilter.retrieve(httpRequestSerializer.deserialize((nettyHttpRequest.content() != null ? nettyHttpRequest.content().toString(CharsetUtil.UTF_8) : "")));
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(expectationSerializer.serialize(expectations).getBytes()));

        } else {

            org.mockserver.model.HttpRequest httpRequest = nettyToMockServerRequestMapper.mapNettyRequestToMockServerRequest(nettyHttpRequest);
            HttpResponse httpResponse = mockServer.handle(httpRequest);
            logFilter.onResponse(httpRequest, httpResponse);
            return mockServerToNettyResponseMapper.mapMockServerResponseToNettyResponse(httpResponse);

        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
