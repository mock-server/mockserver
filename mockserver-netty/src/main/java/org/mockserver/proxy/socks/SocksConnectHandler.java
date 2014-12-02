package org.mockserver.proxy.socks;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socks.*;
import org.mockserver.proxy.relay.RelayConnectHandler;

@ChannelHandler.Sharable
public final class SocksConnectHandler extends RelayConnectHandler<SocksCmdRequest> {

    protected void removeCodecSupport(ChannelHandlerContext ctx) {
        ctx.pipeline().remove(HttpServerCodec.class);
        ctx.pipeline().remove(HttpContentDecompressor.class);
        ctx.pipeline().remove(HttpObjectAggregator.class);
        ctx.pipeline().remove(SocksMessageEncoder.class);
        ctx.pipeline().remove(this);
    }

    protected Object successResponse(Object request) {
        if (request != null && request instanceof SocksCmdRequest) {
            return new SocksCmdResponse(SocksCmdStatus.SUCCESS, ((SocksCmdRequest) request).addressType());
        } else {
            return new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.UNKNOWN);
        }
    }

    protected Object failureResponse(Object request) {
        if (request != null && request instanceof SocksCmdRequest) {
            return new SocksCmdResponse(SocksCmdStatus.FAILURE, ((SocksCmdRequest) request).addressType());
        } else {
            return new SocksCmdResponse(SocksCmdStatus.FAILURE, SocksAddressType.UNKNOWN);
        }
    }
}
