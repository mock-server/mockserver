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
package org.mockserver.netty.proxy.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mappers.MockServerToNettyResponseMapper;
import org.mockserver.mappers.NettyToMockServerRequestMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.NettyHttpRequest;
import org.mockserver.netty.mockserver.NettyMockServer;
import org.mockserver.netty.proxy.http.connect.HexDumpProxyFrontendHandler;
import org.mockserver.proxy.filters.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;

public class HttpProxyHandler extends SimpleChannelInboundHandler<Object> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mockserver
    private final int securePort;
    private final boolean secure;
    private final HttpProxy server;
    private final LogFilter logFilter;
    private final Filters filters = new Filters();
    private final ApacheHttpClient apacheHttpClient = new ApacheHttpClient();
    // mappers
    private NettyToMockServerRequestMapper nettyToMockServerRequestMapper = new NettyToMockServerRequestMapper();
    private MockServerToNettyResponseMapper mockServerToNettyResponseMapper = new MockServerToNettyResponseMapper();
    // serializers
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    // requests
    private NettyHttpRequest mockServerHttpRequest = null;
    private HttpRequest request = null;


    public HttpProxyHandler(LogFilter logFilter, HttpProxy server, int securePort, boolean secure) {
        this.logFilter = logFilter;
        this.server = server;
        this.securePort = securePort;
        this.secure = secure;
        filters.withFilter(new org.mockserver.model.HttpRequest(), new HopByHopHeaderFilter());
        filters.withFilter(new org.mockserver.model.HttpRequest(), logFilter);
    }

    /**
     * Add filter for HTTP requests, each filter get called before each request is proxied, if the filter return null then the request is not proxied
     *
     * @param httpRequest the request to match against for this filter
     * @param filter the filter to execute for this request, if the filter returns null the request will not be proxied
     */
    public HttpProxyHandler withFilter(org.mockserver.model.HttpRequest httpRequest, ProxyRequestFilter filter) {
        filters.withFilter(httpRequest, filter);
        return this;
    }

    /**
     * Add filter for HTTP response, each filter get called after each request has been proxied
     *
     * @param httpRequest the request to match against for this filter
     * @param filter the filter that is executed after this request has been proxied
     */
    public HttpProxyHandler withFilter(org.mockserver.model.HttpRequest httpRequest, ProxyResponseFilter filter) {
        filters.withFilter(httpRequest, filter);
        return this;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpObject && ((HttpObject) msg).getDecoderResult().isSuccess()) {
            if (msg instanceof HttpRequest) {
                request = (HttpRequest) msg;
                String uri = request.getUri();
                if (uri.contains(request.headers().get(HttpHeaders.Names.HOST))) {
                    uri = StringUtils.substringAfter(uri, request.headers().get(HttpHeaders.Names.HOST));
                }
                mockServerHttpRequest = new NettyHttpRequest(request.getProtocolVersion(), request.getMethod(), uri, secure);
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

                    if (mockServerHttpRequest.getMethod() == HttpMethod.CONNECT) {

                        ctx.channel().writeAndFlush(Unpooled.copiedBuffer("HTTP/1.1 200 OK", Charsets.UTF_8)).addListeners(
                                new ChannelFutureListener() {
                                    @Override
                                    public void operationComplete(ChannelFuture future) throws Exception {
                                        ChannelPipeline pipeline = ctx.channel().pipeline();
                                        pipeline.addAfter(ctx.name(), "connect", new HexDumpProxyFrontendHandler("127.0.0.1", securePort));
                                        pipeline.remove(HttpProxyHandler.this);
                                        ctx.fireChannelRead(request);
                                    }
                                }
                        );

                    } else {

                        writeResponse(ctx, mockResponse(mockServerHttpRequest), isKeepAlive(request));

                    }
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

            List<String> typeValues = nettyHttpRequest.parameters().get("type");
            boolean asJava = !typeValues.isEmpty() && "java".equals(typeValues.get(0));
            logFilter.dumpToLog(httpRequestSerializer.deserialize(nettyHttpRequest.content().toString(CharsetUtil.UTF_8)), asJava);
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);

        } else if (nettyHttpRequest.matches(HttpMethod.PUT, "/reset")) {

            logFilter.reset();
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);

        } else if (nettyHttpRequest.matches(HttpMethod.PUT, "/clear")) {

            org.mockserver.model.HttpRequest httpRequest = httpRequestSerializer.deserialize(nettyHttpRequest.content().toString(CharsetUtil.UTF_8));
            logFilter.clear(httpRequest);
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);

        } else if (nettyHttpRequest.matches(HttpMethod.PUT, "/retrieve")) {

            Expectation[] expectations = logFilter.retrieve(httpRequestSerializer.deserialize((nettyHttpRequest.content() != null ? nettyHttpRequest.content().toString(CharsetUtil.UTF_8) : "")));
            String serialize = expectationSerializer.serialize(expectations);
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(serialize.getBytes()));

        } else {

            return forwardRequest(nettyHttpRequest);

        }
    }

    private FullHttpResponse forwardRequest(NettyHttpRequest request) {
        return sendRequest(filters.applyFilters(nettyToMockServerRequestMapper.mapNettyRequestToMockServerRequest(request)));
    }

    private FullHttpResponse sendRequest(final org.mockserver.model.HttpRequest httpRequest) {
        // if HttpRequest was set to null by a filter don't send request
        if (httpRequest != null) {
            HttpResponse httpResponse = filters.applyFilters(httpRequest, apacheHttpClient.sendRequest(httpRequest));
            return mockServerToNettyResponseMapper.mapMockServerResponseToNettyResponse(httpResponse);
        } else {
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
