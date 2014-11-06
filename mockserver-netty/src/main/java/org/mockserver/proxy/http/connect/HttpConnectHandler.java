package org.mockserver.proxy.http.connect;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.mockserver.proxy.http.relay.RelayConnectHandler;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public final class HttpConnectHandler extends RelayConnectHandler<HttpRequest> {

    public HttpConnectHandler(InetSocketAddress connectSocket, boolean secure) {
        super(connectSocket, secure);
    }

    protected void removeCodecSupport(ChannelHandlerContext ctx) {
        ctx.pipeline().remove(HttpServerCodec.class);
        ctx.pipeline().remove(this.getClass());
    }

    protected Object successResponse(Object request) {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    }

    protected Object failureResponse(Object request) {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY);
    }
}
