package org.mockserver.netty.proxy.connect;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import org.mockserver.configuration.Configuration;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.netty.proxy.relay.RelayConnectHandler;
import org.mockserver.codec.MockServerHttpServerCodec;

import static org.mockserver.model.HttpResponse.response;

@ChannelHandler.Sharable
public final class HttpConnectHandler extends RelayConnectHandler<HttpRequest> {

    public HttpConnectHandler(Configuration configuration, LifeCycle server, MockServerLogger mockServerLogger, String host, int port) {
        super(configuration, server, mockServerLogger, host, port);
    }

    protected void removeCodecSupport(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        removeHandler(pipeline, HttpServerCodec.class);
        removeHandler(pipeline, HttpContentDecompressor.class);
        removeHandler(pipeline, HttpObjectAggregator.class);
        removeHandler(pipeline, MockServerHttpServerCodec.class);
        if (pipeline.get(this.getClass()) != null) {
            pipeline.remove(this);
        }
    }

    protected Object successResponse(Object request) {
        return response();
    }

    protected Object failureResponse(Object request) {
        return response().withStatusCode(HttpResponseStatus.BAD_GATEWAY.code());
    }
}
