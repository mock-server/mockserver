package org.mockserver.netty.proxy.socks;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.mockserver.codec.MockServerHttpServerCodec;
import org.mockserver.configuration.Configuration;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.proxy.relay.RelayConnectHandler;

@ChannelHandler.Sharable
public abstract class SocksConnectHandler<T> extends RelayConnectHandler<T> {

    public SocksConnectHandler(Configuration configuration, MockServerLogger mockServerLogger, LifeCycle server, String host, int port) {
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
}
