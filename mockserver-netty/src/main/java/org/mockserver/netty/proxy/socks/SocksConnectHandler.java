package org.mockserver.netty.proxy.socks;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.proxy.relay.RelayConnectHandler;

@ChannelHandler.Sharable
public abstract class SocksConnectHandler<T> extends RelayConnectHandler<T> {

    public SocksConnectHandler(LifeCycle server, MockServerLogger mockServerLogger, String host, int port) {
        super(server, mockServerLogger, host, port);
    }

    protected void removeCodecSupport(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        removeHandler(pipeline, HttpServerCodec.class);
        removeHandler(pipeline, HttpContentDecompressor.class);
        removeHandler(pipeline, HttpObjectAggregator.class);
        removeHandler(pipeline, this);
    }
}
