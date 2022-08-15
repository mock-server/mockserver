package org.mockserver.socket.tls;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.AbstractSniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
public class SniHandler extends AbstractSniHandler<SslContext> {

    private static final AttributeKey<SSLEngine> UPSTREAM_SSL_ENGINE = AttributeKey.valueOf("UPSTREAM_SSL_ENGINE");
    private static final AttributeKey<Certificate[]> UPSTREAM_CLIENT_CERTIFICATES = AttributeKey.valueOf("UPSTREAM_CLIENT_CERTIFICATES");

    private final Configuration configuration;
    private final NettySslContextFactory nettySslContextFactory;

    public SniHandler(Configuration configuration, NettySslContextFactory nettySslContextFactory) {
        this.configuration = configuration;
        this.nettySslContextFactory = nettySslContextFactory;
    }

    @Override
    protected Future<SslContext> lookup(ChannelHandlerContext ctx, String hostname) {
        if (isNotBlank(hostname)) {
            configuration.addSubjectAlternativeName(hostname);
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
            ctx.channel().attr(UPSTREAM_SSL_ENGINE).set(sslHandler.engine());
            ctx.pipeline().replace(this, "SslHandler#0", sslHandler);
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

    public static Certificate[] retrieveClientCertificates(MockServerLogger mockServerLogger, ChannelHandlerContext ctx) {
        Certificate[] clientCertificates = null;
        if (ctx.channel().attr(UPSTREAM_CLIENT_CERTIFICATES).get() != null) {
            clientCertificates = ctx.channel().attr(UPSTREAM_CLIENT_CERTIFICATES).get();
        } else if (ctx.channel().attr(UPSTREAM_SSL_ENGINE).get() != null) {
            SSLEngine sslEngine = ctx.channel().attr(UPSTREAM_SSL_ENGINE).get();
            if (sslEngine != null) {
                SSLSession sslSession = sslEngine.getSession();
                if (sslSession != null) {
                    try {
                        Certificate[] peerCertificates = sslSession.getPeerCertificates();
                        ctx.channel().attr(UPSTREAM_CLIENT_CERTIFICATES).set(peerCertificates);
                        return peerCertificates;
                    } catch (SSLPeerUnverifiedException ignore) {
                        if (MockServerLogger.isEnabled(TRACE) && mockServerLogger != null) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(Level.TRACE)
                                    .setMessageFormat("no client certificate chain as client did not complete mTLS")
                            );
                        }
                    }
                }
            }
        }
        return clientCertificates;
    }
}
