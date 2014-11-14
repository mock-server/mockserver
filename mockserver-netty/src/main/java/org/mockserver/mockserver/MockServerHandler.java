package org.mockserver.mockserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.codec.MockServerToNettyResponseMapper;
import org.mockserver.codec.NettyToMockServerRequestMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.filters.LogFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;

@ChannelHandler.Sharable
public class MockServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mockserver
    private MockServer server;
    private final boolean secure;
    private LogFilter logFilter;
    private MockServerMatcher mockServerMatcher;
    private ActionHandler actionHandler;
    // mappers
    private NettyToMockServerRequestMapper nettyToMockServerRequestMapper = new NettyToMockServerRequestMapper();
    private MockServerToNettyResponseMapper mockServerToNettyResponseMapper = new MockServerToNettyResponseMapper();
    // serializers
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();

    public MockServerHandler(MockServerMatcher mockServerMatcher, LogFilter logFilter, MockServer server, boolean secure) {
        this.mockServerMatcher = mockServerMatcher;
        this.logFilter = logFilter;
        this.server = server;
        this.secure = secure;
        this.actionHandler = new ActionHandler(logFilter);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {

        try {
            MappedRequest mappedRequest = new MappedRequest(request);

            if (mappedRequest.matches(HttpMethod.PUT, "/status")) {

                writeResponse(ctx, request, HttpResponseStatus.OK);

            } else if (mappedRequest.matches(HttpMethod.PUT, "/expectation")) {

                Expectation expectation = expectationSerializer.deserialize(mappedRequest.content());
                mockServerMatcher.when(expectation.getHttpRequest(), expectation.getTimes()).thenRespond(expectation.getHttpResponse(false)).thenForward(expectation.getHttpForward()).thenCallback(expectation.getHttpCallback());
                writeResponse(ctx, request, HttpResponseStatus.CREATED);

            } else if (mappedRequest.matches(HttpMethod.PUT, "/clear")) {

                org.mockserver.model.HttpRequest httpRequest = httpRequestSerializer.deserialize(mappedRequest.content());
                logFilter.clear(httpRequest);
                mockServerMatcher.clear(httpRequest);
                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);

            } else if (mappedRequest.matches(HttpMethod.PUT, "/reset")) {

                logFilter.reset();
                mockServerMatcher.reset();
                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);

            } else if (mappedRequest.matches(HttpMethod.PUT, "/dumpToLog")) {

                mockServerMatcher.dumpToLog(httpRequestSerializer.deserialize(mappedRequest.content()));
                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);

            } else if (mappedRequest.matches(HttpMethod.PUT, "/retrieve")) {

                Expectation[] expectations = logFilter.retrieve(httpRequestSerializer.deserialize(mappedRequest.content()));
                writeResponse(ctx, request, HttpResponseStatus.OK, Unpooled.copiedBuffer(expectationSerializer.serialize(expectations).getBytes()));

            } else if (mappedRequest.matches(HttpMethod.PUT, "/verify")) {

                String result = logFilter.verify(verificationSerializer.deserialize(mappedRequest.content()));
                if (result.isEmpty()) {
                    writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);
                } else {
                    writeResponse(ctx, request, HttpResponseStatus.NOT_ACCEPTABLE, Unpooled.copiedBuffer(result.getBytes()));
                }

            } else if (mappedRequest.matches(HttpMethod.PUT, "/verifySequence")) {

                String result = logFilter.verify(verificationSequenceSerializer.deserialize(mappedRequest.content()));
                if (result.isEmpty()) {
                    writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);
                } else {
                    writeResponse(ctx, request, HttpResponseStatus.NOT_ACCEPTABLE, Unpooled.copiedBuffer(result.getBytes()));
                }

            } else if (mappedRequest.matches(HttpMethod.PUT, "/stop")) {

                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);
                ctx.flush();
                ctx.close();
                server.stop();

            } else {

                HttpRequest httpRequest = nettyToMockServerRequestMapper.mapNettyRequestToMockServerRequest(request, secure);
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
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, responseStatus, responseContent);
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=utf-8");
        writeResponse(ctx, request, response);
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
