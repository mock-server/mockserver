package org.mockserver.netty.proxy.socks;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import org.mockserver.configuration.Configuration;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;

@ChannelHandler.Sharable
public class Socks4ProxyHandler extends SocksProxyHandler<Socks4CommandRequest> {

    public Socks4ProxyHandler(Configuration configuration, MockServerLogger mockServerLogger, LifeCycle server) {
        super(configuration, mockServerLogger, server);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final Socks4CommandRequest commandRequest) {
        if (commandRequest.type().equals(Socks4CommandType.CONNECT)) {
            forwardConnection(ctx, new Socks4ConnectHandler(configuration, mockServerLogger, server, commandRequest.dstAddr(), commandRequest.dstPort()), commandRequest.dstAddr(), commandRequest.dstPort());
            ctx.fireChannelRead(commandRequest);
        } else {
            ctx.writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED)).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
