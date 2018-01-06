package org.mockserver.client.netty;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.util.AttributeKey;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NettyHttpClient {

    static final AttributeKey<Boolean> SECURE = AttributeKey.valueOf("SECURE");
    static final AttributeKey<InetSocketAddress> REMOTE_SOCKET = AttributeKey.valueOf("REMOTE_SOCKET");
    static final AttributeKey<SettableFuture<HttpResponse>> RESPONSE_FUTURE = AttributeKey.valueOf("RESPONSE_FUTURE");
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static EventLoopGroup group = new NioEventLoopGroup();
    private static Bootstrap bootstrap = new Bootstrap()
        .group(group)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.AUTO_READ, true)
        .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
        .handler(new HttpClientInitializer());

    public HttpResponse sendRequest(final HttpRequest httpRequest) throws SocketConnectionException {
        return sendRequest(httpRequest, httpRequest.socketAddressFromHostHeader());
    }

    public HttpResponse sendRequest(final HttpRequest httpRequest, @Nullable InetSocketAddress remoteAddress) throws SocketConnectionException {
        if (remoteAddress == null) {
            remoteAddress = httpRequest.socketAddressFromHostHeader();
        }

        logger.debug("Sending to: {} request: {}", remoteAddress, httpRequest);

        try {
            final SettableFuture<HttpResponse> httpResponseSettableFuture = SettableFuture.create();
            bootstrap
                .attr(SECURE, httpRequest.isSecure() != null && httpRequest.isSecure())
                .attr(REMOTE_SOCKET, remoteAddress)
                .attr(RESPONSE_FUTURE, httpResponseSettableFuture)
                .connect(remoteAddress)
                .addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) {
                        if (future.isSuccess()) {
                            // send the HTTP request
                            future.channel().writeAndFlush(httpRequest);
                        } else {
                            httpResponseSettableFuture.setException(future.cause());
                        }
                    }
                });

            // wait for response
            HttpResponse httpResponse = httpResponseSettableFuture.get(ConfigurationProperties.maxSocketTimeout(), TimeUnit.MILLISECONDS);
            logger.trace("Received response: {}", httpResponse);

            return httpResponse;

        } catch (TimeoutException e) {
            throw new SocketCommunicationException("Response was not received after " + ConfigurationProperties.maxSocketTimeout() + " milliseconds, to make the proxy wait longer please use \"mockserver.maxSocketTimeout\" system property or ConfigurationProperties.maxSocketTimeout(long milliseconds)", e.getCause());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NotSslRecordException) {
                return sendRequest(httpRequest.withSecure(false));
            } else if (cause instanceof ConnectException) {
                throw new SocketConnectionException("Unable to connect to socket " + remoteAddress, cause);
            } else if (cause instanceof UnknownHostException) {
                throw new SocketConnectionException("Unable to resolve host " + remoteAddress, cause);
            } else if (cause instanceof IOException) {
                throw new SocketConnectionException(cause.getMessage(), cause);
            } else {
                throw new RuntimeException("Exception while sending request - " + e.getMessage(), e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Exception while sending request", e);
        }

    }
}
