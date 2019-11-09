package org.mockserver.unification;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.v4.Socks4ServerDecoder;
import io.netty.handler.codec.socksx.v4.Socks4ServerEncoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import org.apache.commons.collections4.KeyValue;
import org.apache.commons.collections4.keyvalue.DefaultKeyValue;
import org.mockserver.callback.CallbackWebSocketServerHandler;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.dashboard.DashboardWebSocketServerHandler;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mockserver.MockServerHandler;
import org.mockserver.proxy.socks.Socks4ProxyHandler;
import org.mockserver.proxy.socks.Socks5ProxyHandler;
import org.mockserver.proxy.socks.SocksDetector;
import org.mockserver.codec.MockServerServerCodec;
import org.mockserver.socket.tls.SniHandler;
import org.slf4j.event.Level;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.unmodifiableSet;
import static org.mockserver.exception.ExceptionHandler.closeOnFlush;
import static org.mockserver.exception.ExceptionHandler.shouldNotIgnoreException;
import static org.mockserver.mockserver.MockServerHandler.LOCAL_HOST_HEADERS;
import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
public class PortUnificationHandler extends ReplayingDecoder<Void> {

    private static final AttributeKey<Boolean> SSL_ENABLED_UPSTREAM = AttributeKey.valueOf("PROXY_SSL_ENABLED_UPSTREAM");
    private static final AttributeKey<Boolean> SSL_ENABLED_DOWNSTREAM = AttributeKey.valueOf("SSL_ENABLED_DOWNSTREAM");
    private static final Map<KeyValue<InetSocketAddress, String>, Set<String>> localAddressesCache = new ConcurrentHashMap<>();

    protected final MockServerLogger mockServerLogger;
    private final LoggingHandler loggingHandler = new LoggingHandler(PortUnificationHandler.class);
    private final HttpContentLengthRemover httpContentLengthRemover = new HttpContentLengthRemover();
    private final LifeCycle server;
    private final HttpStateHandler httpStateHandler;
    private final ActionHandler actionHandler;

    public PortUnificationHandler(LifeCycle server, HttpStateHandler httpStateHandler, ActionHandler actionHandler) {
        this.server = server;
        this.mockServerLogger = httpStateHandler.getMockServerLogger();
        this.httpStateHandler = httpStateHandler;
        this.actionHandler = actionHandler;
    }

    public static void enableSslUpstreamAndDownstream(Channel channel) {
        channel.attr(SSL_ENABLED_UPSTREAM).set(Boolean.TRUE);
        channel.attr(SSL_ENABLED_DOWNSTREAM).set(Boolean.TRUE);
    }

    public static boolean isSslEnabledUpstream(Channel channel) {
        if (channel.attr(SSL_ENABLED_UPSTREAM).get() != null) {
            return channel.attr(SSL_ENABLED_UPSTREAM).get();
        } else {
            return false;
        }
    }

    public static void enableSslDownstream(Channel channel) {
        channel.attr(SSL_ENABLED_DOWNSTREAM).set(Boolean.TRUE);
    }

    public static void disableSslDownstream(Channel channel) {
        channel.attr(SSL_ENABLED_DOWNSTREAM).set(Boolean.FALSE);
    }

    public static boolean isSslEnabledDownstream(Channel channel) {
        if (channel.attr(SSL_ENABLED_DOWNSTREAM).get() != null) {
            return channel.attr(SSL_ENABLED_DOWNSTREAM).get();
        } else {
            return false;
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        if (SocksDetector.isSocks4(msg, actualReadableBytes())) {
            enableSocks4(ctx, msg);
        } else if (SocksDetector.isSocks5(msg, actualReadableBytes())) {
            enableSocks5(ctx, msg);
        } else if (isSsl(msg)) {
            enableSsl(ctx, msg);
        } else if (isHttp(msg)) {
            switchToHttp(ctx, msg);
        } else {
            // Unknown protocol; discard everything and close the connection.
            msg.clear();
            ctx.flush();
            ctx.close();
        }

        addLoggingHandler(ctx);
    }

    private void addLoggingHandler(ChannelHandlerContext ctx) {
        if (mockServerLogger.isEnabled(TRACE)) {
            loggingHandler.addLoggingHandler(ctx);
        }
    }

    private boolean isSsl(ByteBuf buf) {
        return SslHandler.isEncrypted(buf);
    }

    private boolean isHttp(ByteBuf msg) {
        String method = msg.toString(msg.readerIndex(), 8, StandardCharsets.US_ASCII);
        return method.startsWith("GET ") ||
            method.startsWith("POST ") ||
            method.startsWith("PUT ") ||
            method.startsWith("HEAD ") ||
            method.startsWith("OPTIONS ") ||
            method.startsWith("PATCH ") ||
            method.startsWith("DELETE ") ||
            method.startsWith("TRACE ") ||
            method.startsWith("CONNECT ");
    }

    private void enableSocks4(ChannelHandlerContext ctx, ByteBuf msg) {
        enableSocks(ctx, msg, new Socks4ProxyHandler(server, mockServerLogger), Socks4ServerEncoder.INSTANCE, new Socks4ServerDecoder());
    }

    private void enableSocks5(ChannelHandlerContext ctx, ByteBuf msg) {
        enableSocks(ctx, msg, new Socks5ProxyHandler(server, mockServerLogger), Socks5ServerEncoder.DEFAULT, new Socks5InitialRequestDecoder());
    }

    private void enableSocks(ChannelHandlerContext ctx, ByteBuf msg, ChannelHandler... channelHandlers) {
        ChannelPipeline pipeline = ctx.pipeline();
        for (ChannelHandler channelHandler : channelHandlers) {
            pipeline.addFirst(channelHandler);
        }

        // re-unify (with SOCKS5 enabled)
        ctx.pipeline().fireChannelRead(msg.readBytes(actualReadableBytes()));
    }

    private void enableSsl(ChannelHandlerContext ctx, ByteBuf msg) {
        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.addFirst(new SniHandler(mockServerLogger));
        enableSslUpstreamAndDownstream(ctx.channel());

        // re-unify (with SSL enabled)
        ctx.pipeline().fireChannelRead(msg.readBytes(actualReadableBytes()));
    }

    private void    switchToHttp(ChannelHandlerContext ctx, ByteBuf msg) {
        ChannelPipeline pipeline = ctx.pipeline();

        addLastIfNotPresent(pipeline, new HttpServerCodec(
            ConfigurationProperties.maxInitialLineLength(),
            ConfigurationProperties.maxHeaderSize(),
            ConfigurationProperties.maxChunkSize()
        ));
        addLastIfNotPresent(pipeline, new HttpContentDecompressor());
        addLastIfNotPresent(pipeline, httpContentLengthRemover);
        addLastIfNotPresent(pipeline, new HttpObjectAggregator(Integer.MAX_VALUE));
        if (mockServerLogger.isEnabled(TRACE)) {
            addLastIfNotPresent(pipeline, loggingHandler);
        }
        addLastIfNotPresent(pipeline, new CallbackWebSocketServerHandler(httpStateHandler));
        addLastIfNotPresent(pipeline, new DashboardWebSocketServerHandler(httpStateHandler));
        addLastIfNotPresent(pipeline, new MockServerServerCodec(mockServerLogger, isSslEnabledUpstream(ctx.channel())));
        addLastIfNotPresent(pipeline, new MockServerHandler(server, httpStateHandler, actionHandler));
        pipeline.remove(this);

        ctx.channel().attr(LOCAL_HOST_HEADERS).set(getLocalAddresses(ctx));

        // fire message back through pipeline
        ctx.fireChannelRead(msg.readBytes(actualReadableBytes()));
    }

    private Set<String> getLocalAddresses(ChannelHandlerContext ctx) {
        SocketAddress localAddress = ctx.channel().localAddress();
        Set<String> localAddresses = null;
        if (localAddress instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) localAddress;
            String portExtension = calculatePortExtension(inetSocketAddress, isSslEnabledUpstream(ctx.channel()));
            DefaultKeyValue<InetSocketAddress, String> cacheKey = new DefaultKeyValue<>(inetSocketAddress, portExtension);
            localAddresses = localAddressesCache.get(cacheKey);
            if (localAddresses == null) {
                localAddresses = calculateLocalAddresses(inetSocketAddress, portExtension);
                localAddressesCache.put(cacheKey, localAddresses);
            }
        }
        return (localAddresses == null) ? Collections.<String>emptySet() : localAddresses;
    }

    private String calculatePortExtension(InetSocketAddress inetSocketAddress, boolean sslEnabledUpstream) {
        String portExtension;
        if (((inetSocketAddress.getPort() == 443) && sslEnabledUpstream)
            || ((inetSocketAddress.getPort() == 80) && !sslEnabledUpstream)) {

            portExtension = "";
        } else {
            portExtension = ":" + inetSocketAddress.getPort();
        }
        return portExtension;
    }

    private Set<String> calculateLocalAddresses(InetSocketAddress localAddress, String portExtension) {
        InetAddress socketAddress = localAddress.getAddress();
        Set<String> localAddresses = new HashSet<>();
        localAddresses.add(socketAddress.getHostAddress() + portExtension);
        localAddresses.add(socketAddress.getCanonicalHostName() + portExtension);
        localAddresses.add(socketAddress.getHostName() + portExtension);
        localAddresses.add("localhost" + portExtension);
        localAddresses.add("127.0.0.1" + portExtension);
        return unmodifiableSet(localAddresses);
    }

    private void addLastIfNotPresent(ChannelPipeline pipeline, ChannelHandler channelHandler) {
        if (pipeline.get(channelHandler.getClass()) == null) {
            pipeline.addLast(channelHandler);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (shouldNotIgnoreException(cause)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("Exception caught by port unification handler -> closing pipeline " + ctx.channel())
                    .setThrowable(cause)
            );
        }
        closeOnFlush(ctx.channel());
    }
}
