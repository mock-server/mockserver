package org.mockserver.mockserver;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mappers.MockServerToNettyResponseMapper;
import org.mockserver.mappers.NettyToMockServerRequestMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.model.Action;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.NettyHttpRequest;
import org.mockserver.proxy.filters.Filters;
import org.mockserver.proxy.filters.HopByHopHeaderFilter;
import org.mockserver.proxy.filters.LogFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;

public class MockServerHandler extends SimpleChannelInboundHandler<Object> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mockserver
    private final MockServerMatcher mockServerMatcher;
    private final LogFilter logFilter;
    // netty
    private final boolean secure;
    private final MockServer server;
    // request forwarding
    private Filters filters = new Filters();
    private ApacheHttpClient apacheHttpClient = new ApacheHttpClient(true);
    // mappers
    private NettyToMockServerRequestMapper nettyToMockServerRequestMapper = new NettyToMockServerRequestMapper();
    private MockServerToNettyResponseMapper mockServerToNettyResponseMapper = new MockServerToNettyResponseMapper();
    // serializers
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    // requests
    private NettyHttpRequest mockServerHttpRequest = null;
    private HttpRequest request = null;

    public MockServerHandler(MockServerMatcher mockServerMatcher, LogFilter logFilter, MockServer server, boolean secure) {
        this.mockServerMatcher = mockServerMatcher;
        this.logFilter = logFilter;
        this.server = server;
        this.secure = secure;
        filters.withFilter(new org.mockserver.model.HttpRequest(), new HopByHopHeaderFilter());
        filters.withFilter(new org.mockserver.model.HttpRequest(), logFilter);
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

                    if (mockServerHttpRequest.matches(HttpMethod.PUT, "/stop")) {

                        writeResponse(ctx, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED), isKeepAlive(request));
                        ctx.close();
                        server.stop();

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
        ctx.flush();
    }

    @VisibleForTesting
    FullHttpResponse mockResponse(NettyHttpRequest nettyHttpRequest) {

        String content = (nettyHttpRequest.content() != null ? nettyHttpRequest.content().toString(CharsetUtil.UTF_8) : "");

        if (nettyHttpRequest.matches(HttpMethod.PUT, "/dumpToLog")) {

            mockServerMatcher.dumpToLog(httpRequestSerializer.deserialize(content));
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);

        } else if (nettyHttpRequest.matches(HttpMethod.PUT, "/reset")) {

            logFilter.reset();
            mockServerMatcher.reset();
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);

        } else if (nettyHttpRequest.matches(HttpMethod.PUT, "/clear")) {

            org.mockserver.model.HttpRequest httpRequest = httpRequestSerializer.deserialize(content);
            logFilter.clear(httpRequest);
            mockServerMatcher.clear(httpRequest);
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);

        } else if (nettyHttpRequest.matches(HttpMethod.PUT, "/expectation")) {

            Expectation expectation = expectationSerializer.deserialize(content);
            mockServerMatcher.when(expectation.getHttpRequest(), expectation.getTimes()).thenRespond(expectation.getHttpResponse(false)).thenForward(expectation.getHttpForward());
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CREATED);

        } else if (nettyHttpRequest.matches(HttpMethod.PUT, "/retrieve")) {

            Expectation[] expectations = logFilter.retrieve(httpRequestSerializer.deserialize(content));
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(expectationSerializer.serialize(expectations).getBytes()));

        } else {

            org.mockserver.model.HttpRequest httpRequest = nettyToMockServerRequestMapper.mapNettyRequestToMockServerRequest(nettyHttpRequest);
            Action action = mockServerMatcher.handle(httpRequest);
            if (action instanceof HttpForward) {
                HttpForward httpForward = (HttpForward) action;
                nettyHttpRequest.headers().set(HttpHeaders.Names.HOST, httpForward.getHost() + (httpForward.getPort() != null ? ":" + httpForward.getPort() : ""));
                nettyHttpRequest.setSecure(httpForward.getScheme() == HttpForward.Scheme.HTTPS);
                return forwardRequest(nettyHttpRequest);
            } else {
                HttpResponse httpResponse = (HttpResponse) action;
                logFilter.onResponse(httpRequest, httpResponse);
                return mockServerToNettyResponseMapper.mapMockServerResponseToNettyResponse(httpResponse);
            }

        }
    }

    @VisibleForTesting
    FullHttpResponse forwardRequest(NettyHttpRequest request) {
        return sendRequest(filters.applyFilters(nettyToMockServerRequestMapper.mapNettyRequestToMockServerRequest(request)));
    }

    @VisibleForTesting
    FullHttpResponse sendRequest(final org.mockserver.model.HttpRequest httpRequest) {
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
        logger.warn("Exception caught by MockServer handler closing pipeline", cause);
        ctx.close();
    }
}
