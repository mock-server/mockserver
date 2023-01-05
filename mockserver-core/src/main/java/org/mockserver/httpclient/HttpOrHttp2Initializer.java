package org.mockserver.httpclient;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;

import java.util.function.Consumer;

public class HttpOrHttp2Initializer extends ApplicationProtocolNegotiationHandler {

    private final Consumer<ChannelPipeline> http2Initializer;
    private final Consumer<ChannelPipeline> http1Initializer;

    protected HttpOrHttp2Initializer(Consumer<ChannelPipeline> http1Initializer, Consumer<ChannelPipeline> http2Initializer) {
        super("");
        this.http2Initializer = http2Initializer;
        this.http1Initializer = http1Initializer;
    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
        ChannelPipeline pipeline = ctx.pipeline();
        if (pipeline.get(HttpOrHttp2Initializer.class) != null) {
            pipeline.remove(HttpOrHttp2Initializer.class);
        }
        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
            http2Initializer.accept(pipeline);
        } else {
            http1Initializer.accept(pipeline);
        }
    }
}
