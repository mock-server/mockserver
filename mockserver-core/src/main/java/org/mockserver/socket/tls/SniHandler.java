package org.mockserver.socket.tls;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.AbstractSniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author jamesdbloom
 */
public class SniHandler extends AbstractSniHandler<SslContext> {

    private final NettySslContextFactory nettySslContextFactory;

    public SniHandler(NettySslContextFactory nettySslContextFactory) {
        this.nettySslContextFactory = nettySslContextFactory;
    }

    @Override
    protected Future<SslContext> lookup(ChannelHandlerContext ctx, String hostname) {
        if (isNotBlank(hostname)) {
            ConfigurationProperties.addSslSubjectAlternativeNameDomains(hostname);
        }
        return ctx.executor().newSucceededFuture(nettySslContextFactory.createServerSslContext());
    }

    @Override
    protected void onLookupComplete(ChannelHandlerContext ctx, String hostname, Future<SslContext> sslContextFuture) {
        if (!sslContextFuture.isSuccess()) {
            final Throwable cause = sslContextFuture.cause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new DecoderException("Failed to get the SslContext for " + hostname, cause);
        } else {
            try {
                replaceHandler(ctx, sslContextFuture);
            } catch (Throwable cause) {
                PlatformDependent.throwException(cause);
            }
        }
    }

    private void replaceHandler(ChannelHandlerContext ctx, Future<SslContext> sslContext) {
        SslHandler sslHandler = null;
        try {
            sslHandler = sslContext.getNow().newHandler(ctx.alloc());
            ctx.pipeline().replace(this, SslHandler.class.getName(), sslHandler);
            sslHandler = null;
        } finally {
            // Since the SslHandler was not inserted into the pipeline the ownership of the SSLEngine was not
            // transferred to the SslHandler.
            // See https://github.com/netty/netty/issues/5678
            if (sslHandler != null) {
                ReferenceCountUtil.safeRelease(sslHandler.engine());
            }
        }
    }
}
