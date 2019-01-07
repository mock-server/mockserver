package org.mockserver.proxy.socks;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.KeyAndCertificateFactory;

import static org.mockserver.exception.ExceptionHandler.shouldNotIgnoreException;
import static org.mockserver.mockserver.MockServerHandler.PROXYING;
import static org.mockserver.unification.PortUnificationHandler.disableSslDownstream;
import static org.mockserver.unification.PortUnificationHandler.enableSslDownstream;

@ChannelHandler.Sharable
public abstract class SocksProxyHandler<T> extends SimpleChannelInboundHandler<T> {

    protected final LifeCycle server;
    protected final MockServerLogger mockServerLogger;

    public SocksProxyHandler(LifeCycle server, MockServerLogger mockServerLogger) {
        super(false);
        this.server = server;
        this.mockServerLogger = mockServerLogger;
    }

    protected void forwardConnection(final ChannelHandlerContext ctx, ChannelHandler forwarder, final String addr, int port) {
        Channel channel = ctx.channel();
        channel.attr(PROXYING).set(Boolean.TRUE);
        if (String.valueOf(port).endsWith("80")) {
            disableSslDownstream(channel);
        } else if (String.valueOf(port).endsWith("443")) {
            enableSslDownstream(channel);
        }

        // add Subject Alternative Name for SSL certificate
        server.getScheduler().submit(new Runnable() {
            @Override
            public void run() {
                KeyAndCertificateFactory.addSubjectAlternativeName(addr);
            }
        });

        ctx.pipeline().replace(this, null, forwarder);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (shouldNotIgnoreException(cause)) {
            mockServerLogger.error("Exception caught by SOCKS proxy handler -> closing pipeline " + ctx.channel(), cause);
        }
        ctx.close();
    }
}
