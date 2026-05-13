package org.mockserver.httpclient;

import com.google.common.collect.ImmutableMap;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.configuration.Configuration;
import org.mockserver.filters.HopByHopHeaderFilter;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.proxyconfiguration.NoProxyHostsUtils;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.mockserver.model.HttpResponse.response;

public class NettyHttpClient {

    static final AttributeKey<Boolean> SECURE = AttributeKey.valueOf("SECURE");
    static final AttributeKey<InetSocketAddress> REMOTE_SOCKET = AttributeKey.valueOf("REMOTE_SOCKET");
    static final AttributeKey<CompletableFuture<Message>> RESPONSE_FUTURE = AttributeKey.valueOf("RESPONSE_FUTURE");
    static final AttributeKey<Boolean> ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE = AttributeKey.valueOf("ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE");
    private static final HopByHopHeaderFilter hopByHopHeaderFilter = new HopByHopHeaderFilter();
    private final Configuration configuration;
    private final MockServerLogger mockServerLogger;
    private final EventLoopGroup eventLoopGroup;
    private final Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations;
    private final boolean forwardProxyClient;
    private final NettySslContextFactory nettySslContextFactory;

    public NettyHttpClient(Configuration configuration, MockServerLogger mockServerLogger, EventLoopGroup eventLoopGroup, List<ProxyConfiguration> proxyConfigurations, boolean forwardProxyClient) {
        this(configuration, mockServerLogger, eventLoopGroup, proxyConfigurations, forwardProxyClient, new NettySslContextFactory(configuration, mockServerLogger, false));
    }

    public NettyHttpClient(Configuration configuration, MockServerLogger mockServerLogger, EventLoopGroup eventLoopGroup, List<ProxyConfiguration> proxyConfigurations, boolean forwardProxyClient, NettySslContextFactory nettySslContextFactory) {
        this.configuration = configuration;
        this.mockServerLogger = mockServerLogger;
        this.eventLoopGroup = eventLoopGroup;
        this.proxyConfigurations = proxyConfigurations != null ? proxyConfigurations.stream().collect(Collectors.toMap(ProxyConfiguration::getType, proxyConfiguration -> proxyConfiguration)) : ImmutableMap.of();
        this.forwardProxyClient = forwardProxyClient;
        this.nettySslContextFactory = nettySslContextFactory;
    }

    public CompletableFuture<HttpResponse> sendRequest(final HttpRequest httpRequest) throws SocketConnectionException {
        return sendRequest(httpRequest, httpRequest.socketAddressFromHostHeader());
    }

    public CompletableFuture<HttpResponse> sendRequest(final HttpRequest httpRequest, @Nullable InetSocketAddress remoteAddress) throws SocketConnectionException {
        return sendRequest(httpRequest, remoteAddress, configuration.socketConnectionTimeoutInMillis());
    }

    public CompletableFuture<HttpResponse> sendRequest(final HttpRequest httpRequest, @Nullable InetSocketAddress remoteAddress, Long connectionTimeoutMillis) throws SocketConnectionException {
        if (!eventLoopGroup.isShuttingDown()) {
            if (proxyConfigurations != null && !Boolean.TRUE.equals(httpRequest.isSecure())
                && proxyConfigurations.containsKey(ProxyConfiguration.Type.HTTP)
                && isHostNotOnNoProxyHostList(remoteAddress)) {
                ProxyConfiguration proxyConfiguration = proxyConfigurations.get(ProxyConfiguration.Type.HTTP);
                remoteAddress = proxyConfiguration.getProxyAddress();
                proxyConfiguration.addProxyAuthenticationHeader(httpRequest);
            } else if (remoteAddress == null) {
                remoteAddress = httpRequest.socketAddressFromHostHeader();
            }
            if (Protocol.HTTP_2.equals(httpRequest.getProtocol()) && !Boolean.TRUE.equals(httpRequest.isSecure())) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setMessageFormat("HTTP2 requires ALPN but request is not secure (i.e. TLS) so protocol changed to HTTP1")
                );
                httpRequest.withProtocol(Protocol.HTTP_1_1);
            }

            final CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();
            final CompletableFuture<Message> responseFuture = new CompletableFuture<>();
            final Protocol httpProtocol = httpRequest.getProtocol() != null ? httpRequest.getProtocol() : Protocol.HTTP_1_1;

            final HttpClientInitializer clientInitializer = new HttpClientInitializer(proxyConfigurations, mockServerLogger, forwardProxyClient, nettySslContextFactory, httpProtocol);

            final long requestStartedMillis = System.currentTimeMillis();
            final AtomicLong connectionEstablishedMillis = new AtomicLong();

            new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.AUTO_READ, true)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMillis != null ? connectionTimeoutMillis.intValue() : null)
                .attr(SECURE, httpRequest.isSecure() != null && httpRequest.isSecure())
                .attr(REMOTE_SOCKET, remoteAddress)
                .attr(RESPONSE_FUTURE, responseFuture)
                .attr(ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE, true)
                .handler(clientInitializer)
                .connect(remoteAddress)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        connectionEstablishedMillis.set(System.currentTimeMillis());
                        clientInitializer.whenComplete((protocol, throwable) -> {
                            if (throwable != null) {
                                httpResponseFuture.completeExceptionally(throwable);
                            } else {
                                future.channel().writeAndFlush(httpRequest);
                            }
                        });
                    } else {
                        httpResponseFuture.completeExceptionally(future.cause());
                    }
                });

            responseFuture
                .whenComplete((message, throwable) -> {
                    if (throwable == null) {
                        long responseReceivedMillis = System.currentTimeMillis();
                        Timing timing = Timing.timing()
                            .withRequestStartedMillis(requestStartedMillis)
                            .withConnectionEstablishedMillis(connectionEstablishedMillis.get())
                            .withResponseReceivedMillis(responseReceivedMillis)
                            .withConnectionTimeInMillis(connectionEstablishedMillis.get() - requestStartedMillis)
                            .withTotalTimeInMillis(responseReceivedMillis - requestStartedMillis);
                        if (message != null) {
                            HttpResponse response = (HttpResponse) message;
                            response.withTiming(timing);
                            if (forwardProxyClient) {
                                httpResponseFuture.complete(hopByHopHeaderFilter.onResponse(response));
                            } else {
                                httpResponseFuture.complete(response);
                            }
                        } else {
                            httpResponseFuture.complete(response().withTiming(timing));
                        }
                    } else {
                        httpResponseFuture.completeExceptionally(throwable);
                    }
                });

            return httpResponseFuture;
        } else {
            throw new IllegalStateException("Request sent after client has been stopped - the event loop has been shutdown so it is not possible to send a request");
        }
    }

    public CompletableFuture<BinaryMessage> sendRequest(final BinaryMessage binaryRequest, final boolean isSecure, InetSocketAddress remoteAddress, Long connectionTimeoutMillis) throws SocketConnectionException {
        if (!eventLoopGroup.isShuttingDown()) {
            if (proxyConfigurations != null && !isSecure && proxyConfigurations.containsKey(ProxyConfiguration.Type.HTTP)) {
                remoteAddress = proxyConfigurations.get(ProxyConfiguration.Type.HTTP).getProxyAddress();
            } else if (remoteAddress == null) {
                throw new IllegalArgumentException("Remote address cannot be null");
            }

            final CompletableFuture<BinaryMessage> binaryResponseFuture = new CompletableFuture<>();
            final CompletableFuture<Message> responseFuture = new CompletableFuture<>();

            new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.AUTO_READ, true)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMillis != null ? connectionTimeoutMillis.intValue() : null)
                .attr(SECURE, isSecure)
                .attr(REMOTE_SOCKET, remoteAddress)
                .attr(RESPONSE_FUTURE, responseFuture)
                .attr(ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE, !configuration.forwardBinaryRequestsWithoutWaitingForResponse())
                .handler(new HttpClientInitializer(proxyConfigurations, mockServerLogger, forwardProxyClient, nettySslContextFactory, null))
                .connect(remoteAddress)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        if (mockServerLogger.isEnabledForInstance(Level.DEBUG)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(Level.DEBUG)
                                    .setMessageFormat("sending bytes hex{}to{}")
                                    .setArguments(ByteBufUtil.hexDump(binaryRequest.getBytes()), future.channel().attr(REMOTE_SOCKET).get())
                            );
                        }
                        // send the binary request
                        future.channel().writeAndFlush(Unpooled.copiedBuffer(binaryRequest.getBytes()));
                    } else {
                        binaryResponseFuture.completeExceptionally(future.cause());
                    }
                });

            responseFuture
                .whenComplete((message, throwable) -> {
                    if (throwable == null) {
                        binaryResponseFuture.complete((BinaryMessage) message);
                    } else {
                        throwable.printStackTrace();
                        binaryResponseFuture.completeExceptionally(throwable);
                    }
                });

            return binaryResponseFuture;
        } else {
            throw new IllegalStateException("Request sent after client has been stopped - the event loop has been shutdown so it is not possible to send a request");
        }
    }

    public HttpResponse sendRequest(HttpRequest httpRequest, long timeout, TimeUnit unit, boolean ignoreErrors) {
        HttpResponse httpResponse = null;
        try {
            httpResponse = sendRequest(httpRequest).get(timeout, unit);
        } catch (TimeoutException e) {
            if (!ignoreErrors) {
                throw new SocketCommunicationException("Response was not received from MockServer after " + configuration.maxSocketTimeoutInMillis() + " milliseconds, to wait longer please use \"mockserver.maxSocketTimeout\" system property or ConfigurationProperties.maxSocketTimeout(long milliseconds)", e.getCause());
            }
        } catch (InterruptedException | ExecutionException ex) {
            if (!ignoreErrors) {
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
        return httpResponse;
    }

    public HttpResponse sendRequest(HttpRequest httpRequest, long timeout, TimeUnit unit) {
        return sendRequest(httpRequest, timeout, unit, false);
    }

    private boolean isHostNotOnNoProxyHostList(InetSocketAddress remoteAddress) {
        if (remoteAddress == null
            || StringUtils.isBlank(configuration.noProxyHosts())) {
            return true;
        }
        if (NoProxyHostsUtils.isHostOnNoProxyList(remoteAddress.getHostString(), configuration.noProxyHosts())) {
            return false;
        }
        if (remoteAddress.getAddress() != null) {
            String ipAddress = remoteAddress.getAddress().getHostAddress();
            return !NoProxyHostsUtils.isHostOnNoProxyList(ipAddress, configuration.noProxyHosts());
        }
        return true;
    }
}
