package org.mockserver.proxy.http.connect;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import org.mockserver.codec.MockServerServerCodec;
import org.mockserver.model.HttpRequest;
import org.mockserver.proxy.http.relay.RelayConnectHandler;

import java.net.InetSocketAddress;

import static org.mockserver.model.HttpResponse.response;

@ChannelHandler.Sharable
public final class HttpConnectHandler extends RelayConnectHandler<HttpRequest> {

    public HttpConnectHandler(InetSocketAddress connectSocket, boolean secure) {
        super(connectSocket, secure);
    }

    protected void removeCodecSupport(ChannelHandlerContext ctx) {
        ctx.pipeline().remove(HttpServerCodec.class);
        ctx.pipeline().remove(MockServerServerCodec.class);
        ctx.pipeline().remove(this.getClass());
    }

    protected Object successResponse(Object request) {
        return response();
    }

    protected Object failureResponse(Object request) {
        return response().withStatusCode(HttpResponseStatus.BAD_GATEWAY.code());
    }
}
