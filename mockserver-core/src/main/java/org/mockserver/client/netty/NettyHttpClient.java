package org.mockserver.client.netty;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.mockserver.client.netty.proxy.ProxyConfiguration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.mockserver.character.Character.NEW_LINE;

public class NettyHttpClient {

    static final MockServerLogger mockServerLogger = new MockServerLogger(NettyHttpClient.class);
    static final AttributeKey<SettableFuture<HttpResponse>> RESPONSE_FUTURE = AttributeKey.valueOf("RESPONSE_FUTURE");
    static final AttributeKey<SimpleChannelPool> CHANNEL_POOL = AttributeKey.valueOf("CHANNEL_POOL");
    private static EventLoopGroup group = new NioEventLoopGroup();
    private final ProxyConfiguration proxyConfiguration;
    private Bootstrap bootstrap = new Bootstrap()
        .group(group)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.AUTO_READ, true)
        .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024));
    private ChannelPoolMap<ChannelPoolSelector, SimpleChannelPool> poolMap;

    public NettyHttpClient() {
        this(null);
    }

    public NettyHttpClient(final ProxyConfiguration proxyConfiguration) {
        this.proxyConfiguration = proxyConfiguration;
        poolMap = new AbstractChannelPoolMap<ChannelPoolSelector, SimpleChannelPool>() {
            @Override
            protected SimpleChannelPool newPool(final ChannelPoolSelector channelPoolSelector) {
                return new SimpleChannelPool(bootstrap.remoteAddress(channelPoolSelector.remoteAddress), new AbstractChannelPoolHandler() {
                    @Override
                    public void channelCreated(Channel channel) {
                        channel.pipeline().addLast(new HttpClientInitializer(proxyConfiguration, channelPoolSelector));
                    }
                });
            }
        };
    }

    public SettableFuture<HttpResponse> sendRequest(final HttpRequest httpRequest) throws SocketConnectionException {
        return sendRequest(httpRequest, httpRequest.socketAddressFromHostHeader());
    }

    public SettableFuture<HttpResponse> sendRequest(final HttpRequest httpRequest, final @Nullable InetSocketAddress remoteAddress) throws SocketConnectionException {
        final InetSocketAddress connectAddress;

        if (proxyConfiguration != null && proxyConfiguration.getType() == ProxyConfiguration.Type.HTTP) {
            connectAddress = proxyConfiguration.getProxyAddress();
        } else if (remoteAddress == null) {
            connectAddress = httpRequest.socketAddressFromHostHeader();
        } else {
            connectAddress = remoteAddress;
        }

         mockServerLogger.debug("Sending to: {}" + NEW_LINE + "request: {}", connectAddress, httpRequest);

        final SettableFuture<HttpResponse> httpResponseSettableFuture = SettableFuture.create();

        final SimpleChannelPool pool = poolMap.get(new ChannelPoolSelector(connectAddress, httpRequest.isSecure() != null && httpRequest.isSecure()));
        pool.acquire().addListener(new FutureListener<Channel>() {
            @Override
            public void operationComplete(Future<Channel> acquireFuture) {
                if (acquireFuture.isSuccess()) {
                    Channel channel = acquireFuture.getNow();
                    channel.attr(RESPONSE_FUTURE).set(httpResponseSettableFuture);
                    channel.attr(CHANNEL_POOL).set(pool);
                    // send the HTTP request
                    channel.writeAndFlush(httpRequest);
                } else {
                    httpResponseSettableFuture.setException(acquireFuture.cause());
                }
            }
        });

        return httpResponseSettableFuture;
    }

    public HttpResponse sendRequest(HttpRequest httpRequest, long timeout, TimeUnit unit) {
        try {
            return sendRequest(httpRequest).get(timeout, unit);
        } catch (TimeoutException e) {
            throw new SocketCommunicationException("Response was not received from MockServer after " + ConfigurationProperties.maxSocketTimeout() + " milliseconds, to make the proxy wait longer please use \"mockserver.maxSocketTimeout\" system property or ConfigurationProperties.maxSocketTimeout(long milliseconds)", e.getCause());
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

    public static class ChannelPoolSelector {
        public final InetSocketAddress remoteAddress;
        public final boolean isSecure;

        public ChannelPoolSelector(InetSocketAddress remoteAddress, boolean isSecure) {
            this.remoteAddress = remoteAddress;
            this.isSecure = isSecure;
        }
    }
}
