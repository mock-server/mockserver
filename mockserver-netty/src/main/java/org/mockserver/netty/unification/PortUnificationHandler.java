package org.mockserver.netty.unification;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
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
import org.apache.commons.lang3.StringUtils;
import org.mockserver.closurecallback.websocketregistry.CallbackWebSocketServerHandler;
import org.mockserver.codec.MockServerHttpServerCodec;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.dashboard.DashboardWebSocketHandler;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.MockServerHttpResponseToFullHttpResponse;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.action.http.HttpActionHandler;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.HttpRequestHandler;
import org.mockserver.netty.proxy.BinaryHandler;
import org.mockserver.netty.proxy.socks.Socks4ProxyHandler;
import org.mockserver.netty.proxy.socks.Socks5ProxyHandler;
import org.mockserver.netty.proxy.socks.SocksDetector;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.mockserver.socket.tls.SniHandler;
import org.slf4j.event.Level;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.unmodifiableSet;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.tlsMutualAuthenticationRequired;
import static org.mockserver.exception.ExceptionHandling.*;
import static org.mockserver.logging.MockServerLogger.isEnabled;
import static org.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.netty.HttpRequestHandler.LOCAL_HOST_HEADERS;
import static org.mockserver.netty.HttpRequestHandler.PROXYING;
import static org.mockserver.netty.proxy.relay.RelayConnectHandler.*;
import static org.slf4j.event.Level.TRACE;
import static org.slf4j.event.Level.WARN;

/**
 * @author jamesdbloom
 */
public class PortUnificationHandler extends ReplayingDecoder<Void> {

    private static final AttributeKey<Boolean> SSL_ENABLED_UPSTREAM = AttributeKey.valueOf("PROXY_SSL_ENABLED_UPSTREAM");
    private static final AttributeKey<Boolean> SSL_ENABLED_DOWNSTREAM = AttributeKey.valueOf("SSL_ENABLED_DOWNSTREAM");
    private static final AttributeKey<NettySslContextFactory> NETTY_SSL_CONTEXT_FACTORY = AttributeKey.valueOf("NETTY_SSL_CONTEXT_FACTORY");
    private static final Map<PortBinding, Set<String>> localAddressesCache = new ConcurrentHashMap<>();

    protected final MockServerLogger mockServerLogger;
    private final LoggingHandler loggingHandlerFirst = new LoggingHandler(PortUnificationHandler.class.getSimpleName() + "-first");
    private final LoggingHandler loggingHandlerLast = new LoggingHandler(PortUnificationHandler.class.getSimpleName() + "-last");
    private final HttpContentLengthRemover httpContentLengthRemover = new HttpContentLengthRemover();
    private final LifeCycle server;
    private final HttpState httpStateHandler;
    private final HttpActionHandler actionHandler;
    private final NettySslContextFactory nettySslContextFactory;
    private final MockServerHttpResponseToFullHttpResponse mockServerHttpResponseToFullHttpResponse;

    public PortUnificationHandler(LifeCycle server, HttpState httpStateHandler, HttpActionHandler actionHandler, NettySslContextFactory nettySslContextFactory) {
        this.server = server;
        this.mockServerLogger = httpStateHandler.getMockServerLogger();
        this.httpStateHandler = httpStateHandler;
        this.actionHandler = actionHandler;
        this.nettySslContextFactory = nettySslContextFactory;
        this.mockServerHttpResponseToFullHttpResponse = new MockServerHttpResponseToFullHttpResponse(mockServerLogger);
    }

    public static NettySslContextFactory nettySslContextFactory(Channel channel) {
        if (channel.attr(NETTY_SSL_CONTEXT_FACTORY).get() != null) {
            return channel.attr(NETTY_SSL_CONTEXT_FACTORY).get();
        } else {
            throw new RuntimeException("NettySslContextFactory not yet initialised for channel " + channel);
        }
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
        ctx.channel().attr(NETTY_SSL_CONTEXT_FACTORY).set(nettySslContextFactory);
        if (SocksDetector.isSocks4(msg, actualReadableBytes())) {
            logStage(ctx, "adding SOCKS4 decoders");
            enableSocks4(ctx, msg);
        } else if (SocksDetector.isSocks5(msg, actualReadableBytes())) {
            logStage(ctx, "adding SOCKS5 decoders");
            enableSocks5(ctx, msg);
        } else if (isTls(msg)) {
            logStage(ctx, "adding TLS decoders");
            enableTls(ctx, msg);
        } else if (isHttp(msg)) {
            logStage(ctx, "adding HTTP decoders");
            switchToHttp(ctx, msg);
        } else if (isProxyConnected(msg)) {
            logStage(ctx, "setting proxy connected");
            switchToProxyConnected(ctx, msg);
        } else {
            logStage(ctx, "adding binary decoder");
            switchToBinary(ctx, msg);
        }

        if (isEnabled(TRACE)) {
            loggingHandlerFirst.addLoggingHandler(ctx);
        }
        if (isEnabled(TRACE)) {
            ctx.pipeline().addLast(loggingHandlerLast);
        }
    }

    private void logStage(ChannelHandlerContext ctx, String message) {
        if (isEnabled(TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.TRACE)
                    .setMessageFormat(message + " for channel:{}pipeline:{}")
                    .setArguments(ctx.channel().toString(), ctx.pipeline().names())
            );
        }
    }

    private void enableSocks4(ChannelHandlerContext ctx, ByteBuf msg) {
        enableSocks(ctx, msg, new Socks4ServerDecoder(), new Socks4ProxyHandler(server, mockServerLogger), Socks4ServerEncoder.INSTANCE);
    }

    private void enableSocks5(ChannelHandlerContext ctx, ByteBuf msg) {
        enableSocks(ctx, msg, new Socks5InitialRequestDecoder(), new Socks5ProxyHandler(server, mockServerLogger), Socks5ServerEncoder.DEFAULT);
    }

    private void enableSocks(ChannelHandlerContext ctx, ByteBuf msg, ReplayingDecoder<?> socksInitialRequestDecoder, ChannelHandler... channelHandlers) {
        ChannelPipeline pipeline = ctx.pipeline();
        for (ChannelHandler channelHandler : channelHandlers) {
            if (isSslEnabledUpstream(ctx.channel())) {
                pipeline.addAfter(SslHandler.class.getName(), null, channelHandler);
            } else {
                pipeline.addFirst(channelHandler);
            }
        }
        pipeline.addFirst(socksInitialRequestDecoder);

        ctx.channel().attr(PROXYING).set(Boolean.TRUE);

        // re-unify (with SOCKS5 enabled)
        ctx.pipeline().fireChannelRead(msg.readBytes(actualReadableBytes()));
    }

    private boolean isTls(ByteBuf buf) {
        return SslHandler.isEncrypted(buf);
    }

    private void enableTls(ChannelHandlerContext ctx, ByteBuf msg) {
        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.addFirst(new SniHandler(nettySslContextFactory));
        enableSslUpstreamAndDownstream(ctx.channel());

        // re-unify (with SSL enabled)
        ctx.pipeline().fireChannelRead(msg.readBytes(actualReadableBytes()));
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

    private void switchToHttp(ChannelHandlerContext ctx, ByteBuf msg) {
        ChannelPipeline pipeline = ctx.pipeline();

        addLastIfNotPresent(pipeline, new HttpServerCodec(
            ConfigurationProperties.maxInitialLineLength(),
            ConfigurationProperties.maxHeaderSize(),
            ConfigurationProperties.maxChunkSize()
        ));
        addLastIfNotPresent(pipeline, new HttpContentDecompressor());
        addLastIfNotPresent(pipeline, httpContentLengthRemover);
        addLastIfNotPresent(pipeline, new HttpObjectAggregator(Integer.MAX_VALUE));
        if (tlsMutualAuthenticationRequired() && !isSslEnabledUpstream(ctx.channel())) {
            HttpResponse httpResponse = response()
                .withStatusCode(426)
                .withHeader("Upgrade", "TLS/1.2, HTTP/1.1")
                .withHeader("Connection", "Upgrade");
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.INFO)
                    .setMessageFormat("no tls for connection:{}returning response:{}")
                    .setArguments(ctx.channel().localAddress(), httpResponse)
            );
            ctx
                .channel()
                .writeAndFlush(mockServerHttpResponseToFullHttpResponse
                    .mapMockServerResponseToNettyResponse(
                        // Upgrade Required
                        httpResponse
                    ).get(0)
                )
                .addListener((ChannelFuture future) -> future.channel().disconnect().awaitUninterruptibly());
        } else {
            // TODO(jamesdbloom) move to another websocket interactions to another port for performance
            addLastIfNotPresent(pipeline, new CallbackWebSocketServerHandler(httpStateHandler));
            addLastIfNotPresent(pipeline, new DashboardWebSocketHandler(httpStateHandler, isSslEnabledUpstream(ctx.channel()), false));
            addLastIfNotPresent(pipeline, new MockServerHttpServerCodec(mockServerLogger, isSslEnabledUpstream(ctx.channel())));
            addLastIfNotPresent(pipeline, new HttpRequestHandler(server, httpStateHandler, actionHandler));
            pipeline.remove(this);

            ctx.channel().attr(LOCAL_HOST_HEADERS).set(getLocalAddresses(ctx));

            // fire message back through pipeline
            ctx.fireChannelRead(msg.readBytes(actualReadableBytes()));
        }
    }

    private boolean isProxyConnected(ByteBuf msg) {
        return msg.toString(msg.readerIndex(), 8, StandardCharsets.US_ASCII).startsWith(PROXIED);
    }

    private void switchToProxyConnected(ChannelHandlerContext ctx, ByteBuf msg) {
        String message = readMessage(msg);
        if (message.startsWith(PROXIED_SECURE)) {
            String[] hostParts = StringUtils.substringAfter(message, PROXIED_SECURE).split(":");
            int port = hostParts.length > 1 ? Integer.parseInt(hostParts[1]) : 443;
            ctx.channel().attr(PROXYING).set(Boolean.TRUE);
            ctx.channel().attr(REMOTE_SOCKET).set(new InetSocketAddress(hostParts[0], port));
            enableSslUpstreamAndDownstream(ctx.channel());
            ctx.channel().attr(PROXYING).set(Boolean.TRUE);
            ctx.channel().attr(REMOTE_SOCKET).set(new InetSocketAddress(hostParts[0], port));
        } else if (message.startsWith(PROXIED)) {
            String[] hostParts = StringUtils.substringAfter(message, PROXIED).split(":");
            int port = hostParts.length > 1 ? Integer.parseInt(hostParts[1]) : 80;
            ctx.channel().attr(PROXYING).set(Boolean.TRUE);
            ctx.channel().attr(REMOTE_SOCKET).set(new InetSocketAddress(hostParts[0], port));
        }
        ctx.writeAndFlush(Unpooled.copiedBuffer((PROXIED_RESPONSE + message).getBytes(StandardCharsets.UTF_8))).awaitUninterruptibly();
    }

    private String readMessage(ByteBuf msg) {
        byte[] bytes = new byte[actualReadableBytes()];
        msg.readBytes(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private void switchToBinary(ChannelHandlerContext ctx, ByteBuf msg) {
        addLastIfNotPresent(ctx.pipeline(), new BinaryHandler(httpStateHandler.getMockServerLogger(), httpStateHandler.getScheduler(), actionHandler.getHttpClient()));
        // fire message back through pipeline
        ctx.fireChannelRead(msg.readBytes(actualReadableBytes()));
    }

    private Set<String> getLocalAddresses(ChannelHandlerContext ctx) {
        SocketAddress localAddress = ctx.channel().localAddress();
        Set<String> localAddresses = null;
        if (localAddress instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) localAddress;
            String portExtension = calculatePortExtension(inetSocketAddress, isSslEnabledUpstream(ctx.channel()));
            PortBinding cacheKey = new PortBinding(inetSocketAddress, portExtension);
            localAddresses = localAddressesCache.get(cacheKey);
            if (localAddresses == null) {
                localAddresses = calculateLocalAddresses(inetSocketAddress, portExtension);
                localAddressesCache.put(cacheKey, localAddresses);
            }
        }
        return (localAddresses == null) ? Collections.emptySet() : localAddresses;
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
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
        if (connectionClosedException(throwable)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception caught by port unification handler -> closing pipeline " + ctx.channel())
                    .setThrowable(throwable)
            );
        } else if (sslHandshakeException(throwable)) {
            if (throwable.getMessage().contains("certificate_unknown")) {
                if (MockServerLogger.isEnabled(WARN)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.WARN)
                            .setMessageFormat("TSL handshake failure:" + NEW_LINE + NEW_LINE + " Client does not trust MockServer Certificate Authority for:{}See http://mock-server.com/mock_server/HTTPS_TLS.html to enable the client to trust MocksServer Certificate Authority." + NEW_LINE)
                            .setArguments(ctx.channel())
                    );
                }
            } else if (!throwable.getMessage().contains("close_notify during handshake")) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("TSL handshake failure while a client attempted to connect to " + ctx.channel())
                        .setThrowable(throwable)
                );
            }
        }
        closeOnFlush(ctx.channel());
    }
}
