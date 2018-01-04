package org.mockserver.client.netty;

import com.google.common.base.Strings;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.NotSslRecordException;
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

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.mockserver.character.Character.NEW_LINE;

public class NettyHttpClient {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpResponse sendRequest(final HttpRequest httpRequest) throws SocketConnectionException {
        return sendRequest(httpRequest, socketAddressFromHostHeader(httpRequest));
    }

    private InetSocketAddress socketAddressFromHostHeader(HttpRequest request) {
        if (!Strings.isNullOrEmpty(request.getFirstHeader(HOST.toString()))) {
            boolean isSsl = request.isSecure() != null && request.isSecure();
            String[] hostHeaderParts = request.getFirstHeader(HOST.toString()).split(":");
            return new InetSocketAddress(hostHeaderParts[0], hostHeaderParts.length > 1 ? Integer.parseInt(hostHeaderParts[1]) : isSsl ? 443 : 80);
        } else {
            throw new IllegalArgumentException("Host header must be provided for requests being forwarded, the following request does not include the \"Host\" header:" + NEW_LINE + request);
        }
    }

    public HttpResponse sendRequest(final HttpRequest httpRequest, @Nullable InetSocketAddress remoteAddress) throws SocketConnectionException {
        if (remoteAddress == null) {
            remoteAddress = socketAddressFromHostHeader(httpRequest);
        }

        logger.debug("Sending to: {} request: {}", remoteAddress, httpRequest);

        EventLoopGroup group = new NioEventLoopGroup(ConfigurationProperties.nioEventLoopThreadCount());

        try {
            final HttpClientInitializer channelInitializer = new HttpClientInitializer(httpRequest.isSecure() != null && httpRequest.isSecure(), remoteAddress);

            // make the connection attempt
            new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.AUTO_READ, true)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
                .handler(channelInitializer)
                .connect(remoteAddress)
                .addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            // send the HTTP request
                            future.channel().writeAndFlush(httpRequest);
                        } else {
                            channelInitializer.getResponseFuture().setException(future.cause());
                        }
                    }
                });

            // wait for response
            HttpResponse httpResponse = channelInitializer.getResponseFuture().get(ConfigurationProperties.maxSocketTimeout(), TimeUnit.MILLISECONDS);
            logger.trace("Received response: {}", httpResponse);

            // shutdown client
            group.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);

            return httpResponse;

        } catch (TimeoutException e) {
            throw new SocketCommunicationException("Response was not received after " + ConfigurationProperties.maxSocketTimeout() + " milliseconds, to make the proxy wait longer please use \"mockserver.maxSocketTimeout\" system property or ConfigurationProperties.maxSocketTimeout(long milliseconds)", e.getCause());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ConnectException) {
                throw new SocketConnectionException("Unable to connect to socket " + remoteAddress, cause);
            } else if (cause instanceof UnknownHostException) {
                throw new SocketConnectionException("Unable to resolve host " + remoteAddress, cause);
            } else if (cause instanceof NotSslRecordException) {
                return sendRequest(httpRequest.withSecure(false));
            } else if (cause instanceof IOException) {
                throw new SocketConnectionException(cause.getMessage(), cause);
            } else {
                throw new RuntimeException("Exception while sending request - " + e.getMessage(), e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Exception while sending request", e);
        } finally {
            // shut down executor threads to exit
            group.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
        }

    }
}
