package org.mockserver.netty.proxy.socks;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.mockserver.configuration.Configuration;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.exception.ExceptionHandling.connectionClosedException;
import static org.mockserver.netty.HttpRequestHandler.PROXYING;
import static org.mockserver.netty.unification.PortUnificationHandler.disableSslDownstream;
import static org.mockserver.netty.unification.PortUnificationHandler.enableSslDownstream;

@ChannelHandler.Sharable
public abstract class SocksProxyHandler<T> extends SimpleChannelInboundHandler<T> {

    protected final Configuration configuration;
    protected final LifeCycle server;
    protected final MockServerLogger mockServerLogger;

    public SocksProxyHandler(Configuration configuration, MockServerLogger mockServerLogger, LifeCycle server) {
        super(false);
        this.configuration = configuration;
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
        if (isNotBlank(addr)) {
            server.getScheduler().submit(() -> configuration.addSubjectAlternativeName(addr));
        }

        ctx.pipeline().replace(this, null, forwarder);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (connectionClosedException(cause)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception caught by SOCKS proxy handler -> closing pipeline " + ctx.channel())
                    .setThrowable(cause)
            );
        }
        ctx.close();
    }
}
