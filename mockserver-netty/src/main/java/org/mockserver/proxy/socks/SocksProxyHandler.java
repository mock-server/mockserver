package org.mockserver.proxy.socks;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socks.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SocksProxyHandler extends SimpleChannelInboundHandler<SocksRequest> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public SocksProxyHandler() {
        super(false);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, SocksRequest socksRequest) {
        switch (socksRequest.requestType()) {

            case INIT:

                ctx.pipeline().addFirst(SocksCmdRequestDecoder.getName(), new SocksCmdRequestDecoder());
                ctx.write(new SocksInitResponse(SocksAuthScheme.NO_AUTH));
                break;

            case AUTH:

                ctx.pipeline().addFirst(SocksCmdRequestDecoder.getName(), new SocksCmdRequestDecoder());
                ctx.write(new SocksAuthResponse(SocksAuthStatus.SUCCESS));
                break;

            case CMD:

                SocksCmdRequest req = (SocksCmdRequest) socksRequest;
                if (req.cmdType() == SocksCmdType.CONNECT) {

                    ctx.pipeline().addLast(new SocksConnectHandler());
                    ctx.pipeline().remove(this);
                    ctx.fireChannelRead(socksRequest);

                } else {

                    ctx.close();

                }
                break;

            case UNKNOWN:

                ctx.close();
                break;

        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!cause.getMessage().contains("Connection reset by peer")) {
            logger.warn("Exception caught by MockServer handler closing pipeline", cause);
        }
        ctx.close();
    }
}
