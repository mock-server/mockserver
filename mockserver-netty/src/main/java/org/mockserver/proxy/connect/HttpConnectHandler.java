package org.mockserver.proxy.connect;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;
import org.mockserver.codec.MockServerServerCodec;
import org.mockserver.model.HttpRequest;
import org.mockserver.proxy.relay.RelayConnectHandler;

import static org.mockserver.model.HttpResponse.response;

@ChannelHandler.Sharable
public final class HttpConnectHandler extends RelayConnectHandler<HttpRequest> {

    protected void removeCodecSupport(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        removeHandler(pipeline, HttpServerCodec.class);
        removeHandler(pipeline, HttpContentDecompressor.class);
        removeHandler(pipeline, HttpObjectAggregator.class);
        removeHandler(pipeline, MockServerServerCodec.class);
        pipeline.remove(this);
    }

    protected Object successResponse(Object request) {
        return response();
    }

    protected Object failureResponse(Object request) {
        return response().withStatusCode(HttpResponseStatus.BAD_GATEWAY.code());
    }
}
