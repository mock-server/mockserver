package org.mockserver.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.socket.tls.NettySslContextFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NettyHttpClient {

    static final AttributeKey<Boolean> SECURE = AttributeKey.valueOf("SECURE");
    static final AttributeKey<InetSocketAddress> REMOTE_SOCKET = AttributeKey.valueOf("REMOTE_SOCKET");
    static final AttributeKey<CompletableFuture<HttpResponse>> RESPONSE_FUTURE = AttributeKey.valueOf("RESPONSE_FUTURE");
    private final MockServerLogger mockServerLogger;
    private final EventLoopGroup eventLoopGroup;
    private final ProxyConfiguration proxyConfiguration;
    private final boolean forwardProxyClient;
    private final NettySslContextFactory nettySslContextFactory;

    public NettyHttpClient(MockServerLogger mockServerLogger, EventLoopGroup eventLoopGroup, ProxyConfiguration proxyConfiguration, boolean forwardProxyClient) {
        this(mockServerLogger, eventLoopGroup, proxyConfiguration, forwardProxyClient, new NettySslContextFactory(mockServerLogger));
    }

    public NettyHttpClient(MockServerLogger mockServerLogger, EventLoopGroup eventLoopGroup, ProxyConfiguration proxyConfiguration, boolean forwardProxyClient, NettySslContextFactory nettySslContextFactory) {
        this.mockServerLogger = mockServerLogger;
        this.eventLoopGroup = eventLoopGroup;
        this.proxyConfiguration = proxyConfiguration;
        this.forwardProxyClient = forwardProxyClient;
        this.nettySslContextFactory = nettySslContextFactory;
    }

    public CompletableFuture<HttpResponse> sendRequest(final HttpRequest httpRequest) throws SocketConnectionException {
        return sendRequest(httpRequest, httpRequest.socketAddressFromHostHeader());
    }

    public CompletableFuture<HttpResponse> sendRequest(final HttpRequest httpRequest, @Nullable InetSocketAddress remoteAddress) throws SocketConnectionException {
        return sendRequest(httpRequest, remoteAddress, ConfigurationProperties.socketConnectionTimeout());
    }

    public CompletableFuture<HttpResponse> sendRequest(final HttpRequest httpRequest, @Nullable InetSocketAddress remoteAddress, Integer connectionTimeoutMillis) throws SocketConnectionException {
        if (!eventLoopGroup.isShuttingDown()) {
            if (proxyConfiguration != null && proxyConfiguration.getType() == ProxyConfiguration.Type.HTTP) {
                remoteAddress = proxyConfiguration.getProxyAddress();
            } else if (remoteAddress == null) {
                remoteAddress = httpRequest.socketAddressFromHostHeader();
            }

            final CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();
            new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.AUTO_READ, true)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMillis)
                .attr(SECURE, httpRequest.isSecure() != null && httpRequest.isSecure())
                .attr(REMOTE_SOCKET, remoteAddress)
                .attr(RESPONSE_FUTURE, httpResponseFuture)
                .handler(new HttpClientInitializer(proxyConfiguration, mockServerLogger, forwardProxyClient, nettySslContextFactory))
                .connect(remoteAddress)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        // send the HTTP request
                        future.channel().writeAndFlush(httpRequest);
                    } else {
                        httpResponseFuture.completeExceptionally(future.cause());
                    }
                });

            return httpResponseFuture;
        } else {
            throw new IllegalStateException("Request sent after client has been stopped - the event loop has been shutdown so it is not possible to send a request");
        }
    }

    public HttpResponse sendRequest(HttpRequest httpRequest, long timeout, TimeUnit unit) {
        try {
            return sendRequest(httpRequest).get(timeout, unit);
        } catch (TimeoutException e) {
            throw new SocketCommunicationException("Response was not received from MockServer after " + ConfigurationProperties.maxSocketTimeout() + " milliseconds, to wait longer please use \"mockserver.maxSocketTimeout\" system property or ConfigurationProperties.maxSocketTimeout(long milliseconds)", e.getCause());
        } catch (InterruptedException | ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof SocketConnectionException) {
                throw (SocketConnectionException) cause;
            } else if (cause instanceof ConnectException) {
                throw new SocketConnectionException("Unable to connect to socket " + httpRequest.socketAddressFromHostHeader(), cause);
            } else if (cause instanceof UnknownHostException) {
                throw new SocketConnectionException("Unable to resolve host " + httpRequest.socketAddressFromHostHeader(), cause);
            } else if (cause instanceof IOException) {
                throw new SocketConnectionException(cause.getMessage(), cause);
            } else {
                throw new RuntimeException("Exception while sending request - " + ex.getMessage(), ex);
            }
        }
    }
}
