package org.mockserver.client.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.NotSslRecordException;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.OutboundHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NettyHttpClient {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpResponse sendRequest(final OutboundHttpRequest httpRequest) throws SocketConnectionException {
        return sendRequest(httpRequest, false);
    }

    public HttpResponse sendRequest(final OutboundHttpRequest httpRequest, final boolean retryIfSslFails) throws SocketConnectionException {
        logger.debug("Sending request: {}", httpRequest);

        // configure the client
        EventLoopGroup group = new NioEventLoopGroup();

        boolean isSsl = httpRequest.isSecure() != null && httpRequest.isSecure();
        try {
            final HttpClientInitializer channelInitializer = new HttpClientInitializer(isSsl);

            // make the connection attempt
            new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.AUTO_READ, true)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
                    .handler(channelInitializer)
                    .connect(httpRequest.getDestination())
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
            logger.debug("Received response: {}", httpResponse);

            // shutdown client
            group.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);

            return httpResponse;

        } catch (TimeoutException e) {
            throw new SocketCommunicationException("Response was not received after " + ConfigurationProperties.maxSocketTimeout() + " milliseconds, to make the proxy wait longer please use \"mockserver.maxSocketTimeout\" system property or ConfigurationProperties.maxSocketTimeout(long milliseconds)", e.getCause());
        } catch (ExecutionException e) {
            if (retryIfSslFails) {
                return sendRequest(httpRequest.withSsl(!isSsl));
            } else {
                Throwable cause = e.getCause();
                if (cause instanceof ConnectException) {
                    throw new SocketConnectionException("Unable to connect to socket " + httpRequest.getDestination(), cause);
                } else if (cause instanceof UnknownHostException) {
                    throw new SocketConnectionException("Unable to resolve host " + httpRequest.getDestination(), cause);
                } else if (cause instanceof NotSslRecordException) {
                    return sendRequest(httpRequest.withSsl(false));
                } else if (cause instanceof IOException) {
                    throw new SocketConnectionException(cause.getMessage(), cause);
                } else {
                    throw new RuntimeException("Exception while sending request", e);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Exception while sending request", e);
        } finally {
            // shut down executor threads to exit
            group.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
        }
    }
}
