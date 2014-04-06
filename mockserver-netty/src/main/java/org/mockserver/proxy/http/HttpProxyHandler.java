package org.mockserver.proxy.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.socks.*;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.HttpRequestSerializer;
import org.mockserver.mappers.MockServerToNettyResponseMapper;
import org.mockserver.mappers.NettyToMockServerRequestMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.NettyHttpRequest;
import org.mockserver.proxy.filters.*;
import org.mockserver.proxy.http.connect.HttpConnectHandler;
import org.mockserver.proxy.http.socks.SocksConnectHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;

public class HttpProxyHandler extends SimpleChannelInboundHandler<Object> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mockserver
    private final InetSocketAddress connectSocket;
    private final boolean secure;
    private final HttpProxy server;
    private final LogFilter logFilter;
    private final Filters filters = new Filters();
    private final ApacheHttpClient apacheHttpClient = new ApacheHttpClient(true);
    // mappers
    private NettyToMockServerRequestMapper nettyToMockServerRequestMapper = new NettyToMockServerRequestMapper();
    private MockServerToNettyResponseMapper mockServerToNettyResponseMapper = new MockServerToNettyResponseMapper();
    // serializers
    private ExpectationSerializer expectationSerializer = new ExpectationSerializer();
    private HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer();
    // requests
    private NettyHttpRequest mockServerHttpRequest = null;
    private HttpRequest request = null;


    public HttpProxyHandler(LogFilter logFilter, HttpProxy server, InetSocketAddress connectSocket, boolean secure) {
        this.logFilter = logFilter;
        this.server = server;
        this.connectSocket = connectSocket;
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

                    if (connectSocket != null && mockServerHttpRequest.getMethod() == HttpMethod.CONNECT) {

                        ctx.pipeline().addAfter(ctx.name(), HttpConnectHandler.class.getSimpleName(), new HttpConnectHandler(connectSocket, true));
                        ctx.pipeline().remove(this);
                        ctx.fireChannelRead(request);

                    } else if (mockServerHttpRequest.matches(HttpMethod.PUT, "/stop")) {

                        writeResponse(ctx, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED), isKeepAlive(request));
                        ctx.close();
                        if (server != null) {
                            server.stop();
                        } else {
                            System.exit(0);
                        }

                    } else {

                        writeResponse(ctx, mockResponse(mockServerHttpRequest), isKeepAlive(request));

                    }
                }

            }
        } else if (msg instanceof SocksRequest) {
            SocksRequest socksRequest = (SocksRequest) msg;
            switch (socksRequest.requestType()) {

                case INIT:

                    ctx.pipeline().addFirst(SocksCmdRequestDecoder.getName(), new SocksCmdRequestDecoder());
                    ctx.write(new SocksInitResponse(SocksAuthScheme.NO_AUTH));
                    break;

                case AUTH:

                    ctx.pipeline().addFirst(SocksCmdRequestDecoder.getName(), new SocksCmdRequestDecoder());
                    ctx.write(new SocksAuthResponse(SocksAuthStatus.SUCCESS));
                    break;

                case CMD:

                    SocksCmdRequest req = (SocksCmdRequest) socksRequest;
                    if (req.cmdType() == SocksCmdType.CONNECT) {

                        ctx.pipeline().addLast(SocksConnectHandler.class.getSimpleName(), new SocksConnectHandler(connectSocket, secure));
                        ctx.pipeline().remove(this);
                        ctx.fireChannelRead(socksRequest);

                    } else {

                        ctx.close();

                    }
                    break;

                case UNKNOWN:

                    ctx.close();
                    break;

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

    private FullHttpResponse mockResponse(NettyHttpRequest nettyHttpRequest) {

        if (nettyHttpRequest.matches(HttpMethod.PUT, "/dumpToLog")) {

            List<String> typeValues = nettyHttpRequest.parameters().get("type");
            boolean asJava = typeValues != null && !typeValues.isEmpty() && "java".equals(typeValues.get(0));
            logFilter.dumpToLog((nettyHttpRequest.content() != null ? httpRequestSerializer.deserialize(nettyHttpRequest.content().toString(CharsetUtil.UTF_8)) : null), asJava);
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
        logger.warn("Exception caught by http proxy handler closing pipeline", cause);
        ctx.close();
    }
}
