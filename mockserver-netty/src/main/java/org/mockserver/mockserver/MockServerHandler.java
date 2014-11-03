package org.mockserver.mockserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mappers.MockServerToNettyResponseMapper;
import org.mockserver.mappers.NettyToMockServerRequestMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.Action;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.NettyHttpRequest;
import org.mockserver.proxy.filters.LogFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;

@ChannelHandler.Sharable
public class MockServerHandler extends SimpleChannelInboundHandler<NettyHttpRequest> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mockserver
    private MockServer server;
    private LogFilter logFilter;
    private MockServerMatcher mockServerMatcher;
    private ActionHandler actionHandler;
    // mappers
    private NettyToMockServerRequestMapper nettyToMockServerRequestMapper = new NettyToMockServerRequestMapper();
    private MockServerToNettyResponseMapper mockServerToNettyResponseMapper = new MockServerToNettyResponseMapper();
    // serializers
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();

    public MockServerHandler(MockServerMatcher mockServerMatcher, LogFilter logFilter, MockServer server) {
        this.mockServerMatcher = mockServerMatcher;
        this.logFilter = logFilter;
        this.server = server;
        this.actionHandler = new ActionHandler(logFilter);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NettyHttpRequest request) {

        try {
            String content = (request.content() != null ? request.content().toString(CharsetUtil.UTF_8) : "");

            if (request.matches(HttpMethod.PUT, "/dumpToLog")) {

                mockServerMatcher.dumpToLog(httpRequestSerializer.deserialize(content));
                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);

            } else if (request.matches(HttpMethod.PUT, "/reset")) {

                logFilter.reset();
                mockServerMatcher.reset();
                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);

            } else if (request.matches(HttpMethod.PUT, "/clear")) {

                org.mockserver.model.HttpRequest httpRequest = httpRequestSerializer.deserialize(content);
                logFilter.clear(httpRequest);
                mockServerMatcher.clear(httpRequest);
                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);

            } else if (request.matches(HttpMethod.PUT, "/expectation")) {

                Expectation expectation = expectationSerializer.deserialize(content);
                mockServerMatcher.when(expectation.getHttpRequest(), expectation.getTimes()).thenRespond(expectation.getHttpResponse(false)).thenForward(expectation.getHttpForward()).thenCallback(expectation.getHttpCallback());
                writeResponse(ctx, request, HttpResponseStatus.CREATED);

            } else if (request.matches(HttpMethod.PUT, "/retrieve")) {

                Expectation[] expectations = logFilter.retrieve(httpRequestSerializer.deserialize(content));
                writeResponse(ctx, request, HttpResponseStatus.OK, Unpooled.copiedBuffer(expectationSerializer.serialize(expectations).getBytes()));

            } else if (request.matches(HttpMethod.PUT, "/stop")) {

                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);
                ctx.close();
                server.stop();

            } else {

                HttpRequest httpRequest = nettyToMockServerRequestMapper.mapNettyRequestToMockServerRequest(request);
                writeResponse(ctx, request, actionHandler.processAction(mockServerMatcher.handle(httpRequest), httpRequest));

            }
        } catch (Exception e) {
            logger.error("Exception processing " + request, e);
            writeResponse(ctx, request, HttpResponseStatus.BAD_REQUEST);
        }

    }

    private void writeResponse(ChannelHandlerContext ctx, HttpMessage request, HttpResponse httpResponse) {
        if (httpResponse != null) {
            writeResponse(ctx, request, mockServerToNettyResponseMapper.mapMockServerResponseToNettyResponse(httpResponse));
        } else {
            writeResponse(ctx, request, HttpResponseStatus.NOT_FOUND);
        }
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpMessage request, HttpResponseStatus responseStatus) {
        writeResponse(ctx, request, responseStatus, Unpooled.buffer(0));
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpMessage request, HttpResponseStatus responseStatus, ByteBuf responseContent) {
        writeResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, responseStatus, responseContent));
    }

    private void writeResponse(ChannelHandlerContext ctx, HttpMessage request, FullHttpResponse response) {
        if (isKeepAlive(request)) {
            response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        } else {
            response.headers().set(CONNECTION, HttpHeaders.Values.CLOSE);
        }

        ChannelFuture future = ctx.write(response);

        if (!isKeepAlive(request)) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!cause.getMessage().contains("Connection reset by peer")) {
            logger.warn("Exception caught by MockServer handler closing pipeline", cause);
        }
        ctx.close();
    }
}
