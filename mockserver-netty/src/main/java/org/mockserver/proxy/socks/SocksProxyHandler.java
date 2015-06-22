package org.mockserver.proxy.socks;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socks.*;
import org.mockserver.proxy.unification.PortUnificationHandler;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockserver.proxy.error.Logging.shouldIgnoreException;

public class SocksProxyHandler extends SimpleChannelInboundHandler<SocksRequest> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public SocksProxyHandler() {
        super(false);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, SocksRequest socksRequest) {
        switch (socksRequest.requestType()) {

            case INIT:

                ctx.pipeline().addFirst(new SocksCmdRequestDecoder());
                ctx.write(new SocksInitResponse(SocksAuthScheme.NO_AUTH));
                break;

            case AUTH:

                ctx.pipeline().addFirst(new SocksCmdRequestDecoder());
                ctx.write(new SocksAuthResponse(SocksAuthStatus.SUCCESS));
                break;

            case CMD:

                SocksCmdRequest req = (SocksCmdRequest) socksRequest;
                if (req.cmdType() == SocksCmdType.CONNECT) {

                    if (req.port() == 80 || req.port() == 8080) {
                        PortUnificationHandler.disableSslDownstream(ctx.channel());
                    } else if (req.port() == 443 || req.port() == 8443) {
                        PortUnificationHandler.enabledSslDownstream(ctx.channel());
                    } else {
                        // assume SSL enabled by default, if this is incorrect client retries without SSL
                        PortUnificationHandler.enabledSslDownstream(ctx.channel());
                    }

                    // add Subject Alternative Name for SSL certificate
                    SSLFactory.addSubjectAlternativeName(req.host());

                    ctx.pipeline().addAfter(getClass().getSimpleName() + "#0", SocksConnectHandler.class.getSimpleName() + "#0", new SocksConnectHandler());
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
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!shouldIgnoreException(cause)) {
            logger.warn("Exception caught by SOCKS proxy handler -> closing pipeline " + ctx.channel(), cause);
        }
        ctx.close();
    }
}
