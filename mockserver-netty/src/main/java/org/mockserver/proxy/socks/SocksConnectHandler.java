package org.mockserver.proxy.socks;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socks.*;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.proxy.relay.RelayConnectHandler;

@ChannelHandler.Sharable
public final class SocksConnectHandler extends RelayConnectHandler<SocksCmdRequest> {

    public SocksConnectHandler(String host, int port) {
        super(host, port);
    }

    protected void removeCodecSupport(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        removeHandler(pipeline, SslHandler.class);
        removeHandler(pipeline, HttpServerCodec.class);
        removeHandler(pipeline, HttpContentDecompressor.class);
        removeHandler(pipeline, HttpObjectAggregator.class);
        removeHandler(pipeline, SocksMessageEncoder.class);
        removeHandler(pipeline, this);
    }

    protected Object successResponse(Object request) {
        if (request != null && request instanceof SocksCmdRequest) {
            return new SocksCmdResponse(SocksCmdStatus.SUCCESS, ((SocksCmdRequest) request).addressType(), ((SocksCmdRequest) request).host(), ((SocksCmdRequest) request).port());
        } else {
            return new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.UNKNOWN);
        }
    }

    protected Object failureResponse(Object request) {
        if (request != null && request instanceof SocksCmdRequest) {
            return new SocksCmdResponse(SocksCmdStatus.FAILURE, ((SocksCmdRequest) request).addressType(), ((SocksCmdRequest) request).host(), ((SocksCmdRequest) request).port());
        } else {
            return new SocksCmdResponse(SocksCmdStatus.FAILURE, SocksAddressType.UNKNOWN);
        }
    }
}
