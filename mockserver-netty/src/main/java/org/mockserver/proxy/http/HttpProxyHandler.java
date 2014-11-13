package org.mockserver.proxy.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.mockserver.client.http.NettyHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.client.serialization.VerificationSequenceSerializer;
import org.mockserver.client.serialization.VerificationSerializer;
import org.mockserver.filters.Filters;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.filters.LogFilter;
import org.mockserver.mappers.MockServerToNettyResponseMapper;
import org.mockserver.mappers.NettyToMockServerRequestMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.mockserver.MappedRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxy.http.connect.HttpConnectHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;

@ChannelHandler.Sharable
public class HttpProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mockserver
    private final InetSocketAddress connectSocket;
    private final boolean secure;
    private final HttpProxy server;
    private final LogFilter logFilter;
    private final Filters filters = new Filters();
    // http client
    private NettyHttpClient httpClient = new NettyHttpClient();
    // mappers
    private NettyToMockServerRequestMapper nettyToMockServerRequestMapper = new NettyToMockServerRequestMapper();
    private MockServerToNettyResponseMapper mockServerToNettyResponseMapper = new MockServerToNettyResponseMapper();
    // serializers
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    private VerificationSerializer verificationSerializer = new VerificationSerializer();
    private VerificationSequenceSerializer verificationSequenceSerializer = new VerificationSequenceSerializer();

    public HttpProxyHandler(LogFilter logFilter, HttpProxy server, InetSocketAddress connectSocket, boolean secure) {
        super(false); // TODO(jamesdbloom): why does this need to be autorelease false??
        this.logFilter = logFilter;
        this.server = server;
        this.connectSocket = connectSocket;
        this.secure = secure;
        filters.withFilter(new org.mockserver.model.HttpRequest(), new HopByHopHeaderFilter());
        filters.withFilter(new org.mockserver.model.HttpRequest(), logFilter);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {

        try {
            MappedRequest mappedRequest = new MappedRequest(request);

            if (connectSocket != null && mappedRequest.method().equals(HttpMethod.CONNECT)) {

                ctx.pipeline().addAfter(ctx.name(), HttpConnectHandler.class.getSimpleName(), new HttpConnectHandler(connectSocket, true));
                ctx.pipeline().remove(this);
                ctx.fireChannelRead(request);

            } else if (mappedRequest.matches(HttpMethod.PUT, "/status")) {

                writeResponse(ctx, request, HttpResponseStatus.OK);

            } else if (mappedRequest.matches(HttpMethod.PUT, "/clear")) {

                org.mockserver.model.HttpRequest httpRequest = httpRequestSerializer.deserialize(mappedRequest.content());
                logFilter.clear(httpRequest);
                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);

            } else if (mappedRequest.matches(HttpMethod.PUT, "/reset")) {

                logFilter.reset();
                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);

            } else if (mappedRequest.matches(HttpMethod.PUT, "/dumpToLog")) {

                List<String> typeValues = mappedRequest.parameters().get("type");
                boolean asJava = typeValues != null && !typeValues.isEmpty() && "java".equals(typeValues.get(0));
                logFilter.dumpToLog((mappedRequest.content() != null ? httpRequestSerializer.deserialize(mappedRequest.content()) : null), asJava);
                writeResponse(ctx, request, HttpResponseStatus.ACCEPTED);

            } else if (mappedRequest.matches(HttpMethod.PUT, "/retrieve")) {

                Expectation[] expectations = logFilter.retrieve(httpRequestSerializer.deserialize(mappedRequest.content()));
                String serialize = expectationSerializer.serialize(expectations);
                writeResponse(ctx, request, HttpResponseStatus.OK, Unpooled.copiedBuffer(serialize.getBytes()));

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

                writeResponse(ctx, request, forwardRequest(request));

            }
        } catch (Exception e) {
            logger.error("Exception processing " + request, e);
            writeResponse(ctx, request, HttpResponseStatus.BAD_REQUEST);
        }

    }

    private FullHttpResponse forwardRequest(FullHttpRequest nettyHttpRequest) {
        return sendRequest(filters.applyOnRequestFilters(nettyToMockServerRequestMapper.mapNettyRequestToMockServerRequest(nettyHttpRequest, secure)));
    }

    private FullHttpResponse sendRequest(final org.mockserver.model.HttpRequest httpRequest) {
        // if HttpRequest was set to null by a filter don't send request
        if (httpRequest != null) {
            HttpResponse httpResponse = filters.applyOnResponseFilters(httpRequest, httpClient.sendRequest(httpRequest));
            return mockServerToNettyResponseMapper.mapMockServerResponseToNettyResponse(httpResponse);
        } else {
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
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
