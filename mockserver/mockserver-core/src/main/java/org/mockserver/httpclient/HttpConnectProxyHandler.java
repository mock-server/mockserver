package org.mockserver.httpclient;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeadersFactory;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.TooLongHttpHeaderException;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.HttpProxyHandler.HttpProxyConnectException;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import org.mockserver.codec.HttpLineEndSplitGuard;
import org.mockserver.logging.MockServerLogger;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Opens a tunnel through an upstream HTTP proxy with {@code CONNECT}, as Netty's {@link HttpProxyHandler} does, but
 * reads the proxy's response headers up to {@code maxHeaderSize}: {@code HttpProxyHandler} is final and builds its
 * codec with Netty's 8,192-byte default, and waits out its connect timeout on a response with larger headers.
 * <p>
 * The request it sends and the failures it reports are {@code HttpProxyHandler}'s, which
 * {@code HttpConnectProxyHandlerTest} compares; a response with headers over the limit fails the connection with a
 * {@link HeaderLimitExceededException} as its cause.
 */
final class HttpConnectProxyHandler extends ProxyHandler {

    private final MockServerLogger mockServerLogger;
    private final int maxHeaderSize;
    private final HttpClientCodec codec;
    private final HttpLineEndSplitGuard lineEndSplitGuard;
    private final CharSequence authorization;
    private HttpResponseStatus status;
    private HttpHeaders inboundHeaders;

    HttpConnectProxyHandler(SocketAddress proxyAddress, String username, String password, MockServerLogger mockServerLogger, int maxHeaderSize) {
        super(proxyAddress);
        this.mockServerLogger = mockServerLogger;
        this.maxHeaderSize = maxHeaderSize;
        this.codec = new HttpClientCodec(HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH, maxHeaderSize, HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE);
        this.lineEndSplitGuard = new HttpLineEndSplitGuard(codec);
        this.authorization = username != null && password != null ? basic(username, password) : null;
    }

    private static CharSequence basic(String username, String password) {
        ByteBuf credentials = Unpooled.copiedBuffer(username + ':' + password, CharsetUtil.UTF_8);
        try {
            ByteBuf encoded = Base64.encode(credentials, false);
            try {
                return new AsciiString("Basic " + encoded.toString(CharsetUtil.US_ASCII));
            } finally {
                encoded.release();
            }
        } finally {
            credentials.release();
        }
    }

    /**
     * The status the proxy answered the {@code CONNECT} with, when that was not 200; null otherwise.
     */
    HttpResponseStatus refusedWith() {
        return status != null && status.code() != 200 ? status : null;
    }

    @Override
    public String protocol() {
        return "http";
    }

    @Override
    public String authScheme() {
        return authorization != null ? "basic" : "none";
    }

    @Override
    protected void addCodec(ChannelHandlerContext ctx) {
        ctx.pipeline().addBefore(ctx.name(), null, lineEndSplitGuard.beforeCodec());
        ctx.pipeline().addBefore(ctx.name(), null, codec);
        ctx.pipeline().addBefore(ctx.name(), null, lineEndSplitGuard.afterCodec());
        // once the tunnel is open the codec is an empty shell; a look-up of the forward codec by type must not find it
        connectFuture().addListener(connected -> {
            if (connected.isSuccess() && ctx.pipeline().context(codec) != null) {
                ctx.pipeline().remove(codec);
            }
            if (connected.isSuccess()) {
                lineEndSplitGuard.remove(ctx.pipeline());
            }
        });
    }

    @Override
    protected void removeEncoder(ChannelHandlerContext ctx) {
        codec.removeOutboundHandler();
    }

    @Override
    protected void removeDecoder(ChannelHandlerContext ctx) {
        codec.removeInboundHandler();
    }

    @Override
    protected Object newInitialMessage(ChannelHandlerContext ctx) {
        InetSocketAddress destination = destinationAddress();
        String hostAndPort = HttpUtil.formatHostnameForHttp(destination) + ":" + destination.getPort();
        HttpHeadersFactory headersFactory = DefaultHttpHeadersFactory.headersFactory().withValidation(true);
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, hostAndPort, Unpooled.EMPTY_BUFFER, headersFactory, headersFactory);
        request.headers().set(HttpHeaderNames.HOST, hostAndPort);
        if (authorization != null) {
            request.headers().set(HttpHeaderNames.PROXY_AUTHORIZATION, authorization);
        }
        return request;
    }

    @Override
    protected boolean handleResponse(ChannelHandlerContext ctx, Object response) throws Exception {
        if (response instanceof HttpObject && ((HttpObject) response).decoderResult().cause() instanceof TooLongHttpHeaderException) {
            throw ForwardHeaderLimit.responseOverLimit(mockServerLogger, ctx.channel(), ForwardHeaderLimit.CONNECT_RESPONSE_HEADERS, maxHeaderSize);
        }
        if (response instanceof HttpResponse) {
            if (status != null) {
                throw new HttpProxyConnectException(exceptionMessage("too many responses"), null);
            }
            status = ((HttpResponse) response).status();
            inboundHeaders = ((HttpResponse) response).headers();
        }
        boolean finished = response instanceof LastHttpContent;
        if (finished) {
            if (status == null) {
                throw new HttpProxyConnectException(exceptionMessage("missing response"), inboundHeaders);
            }
            if (status.code() != 200) {
                throw new HttpProxyConnectException(exceptionMessage("status: " + status), inboundHeaders);
            }
        }
        return finished;
    }
}
