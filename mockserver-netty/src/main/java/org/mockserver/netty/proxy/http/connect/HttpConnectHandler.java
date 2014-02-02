package org.mockserver.netty.proxy.http.connect;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.mockserver.netty.proxy.http.relay.RelayConnectHandler;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public final class HttpConnectHandler extends RelayConnectHandler<HttpRequest> {

    public HttpConnectHandler(InetSocketAddress connectSocket) {
        super(connectSocket);
    }

    protected void removeCodecSupport(ChannelHandlerContext ctx) {
        ctx.pipeline().remove(HttpServerCodec.class.getSimpleName());
        ctx.pipeline().remove(HttpConnectHandler.class.getSimpleName());
    }

    protected Object successResponse(Object request) {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    }

    protected Object failureResponse(Object request) {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY);
    }
}
