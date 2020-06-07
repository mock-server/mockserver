package org.mockserver.netty.proxy.socks;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v4.Socks4ServerEncoder;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;

@ChannelHandler.Sharable
public final class Socks4ConnectHandler extends SocksConnectHandler<Socks4CommandRequest> {

    public Socks4ConnectHandler(LifeCycle server, MockServerLogger mockServerLogger, String host, int port) {
        super(server, mockServerLogger, host, port);
    }

    protected void removeCodecSupport(ChannelHandlerContext ctx) {
        super.removeCodecSupport(ctx);
        removeHandler(ctx.pipeline(), Socks4ServerEncoder.class);
    }

    protected Object successResponse(Object request) {
        return new DefaultSocks4CommandResponse(Socks4CommandStatus.SUCCESS, host, port);
    }

    protected Object failureResponse(Object request) {
        return new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED, host, port);
    }
}
