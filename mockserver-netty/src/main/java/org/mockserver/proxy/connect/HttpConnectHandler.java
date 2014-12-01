package org.mockserver.proxy.connect;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.mockserver.codec.MockServerServerCodec;
import org.mockserver.model.HttpRequest;
import org.mockserver.proxy.relay.RelayConnectHandler;

import static org.mockserver.model.HttpResponse.response;

@ChannelHandler.Sharable
public final class HttpConnectHandler extends RelayConnectHandler<HttpRequest> {

    protected void removeCodecSupport(ChannelHandlerContext ctx) {
        ctx.pipeline().remove(HttpServerCodec.class);
        ctx.pipeline().remove(HttpContentDecompressor.class);
        ctx.pipeline().remove(HttpObjectAggregator.class);
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
