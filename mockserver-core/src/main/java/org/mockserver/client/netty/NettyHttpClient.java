package org.mockserver.client.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.OutboundHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class NettyHttpClient {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpResponse sendRequest(final OutboundHttpRequest httpRequest) throws SocketConnectionException {
        logger.debug("Sending request: {}", httpRequest);

        // configure the client
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            final HttpClientInitializer channelInitializer = new HttpClientInitializer(httpRequest.isSecure());

            // make the connection attempt
            new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
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
            HttpResponse httpResponse = channelInitializer.getResponseFuture().get();
            logger.debug("Received response: {}", httpResponse);

            // shutdown client
            group.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);

            return httpResponse;

        } catch (ExecutionException e) {
            if (e.getCause() instanceof ConnectException) {
                throw new SocketConnectionException("Unable to connect to socket " + httpRequest.getDestination(), e.getCause());
            } else if (e.getCause() instanceof UnknownHostException) {
                throw new SocketConnectionException("Unable to resolve host " + httpRequest.getDestination(), e.getCause());
            } else if (e.getCause() instanceof IOException) {
                throw new SocketCommunicationException("Error while communicating to " + httpRequest.getDestination(), e.getCause());
            } else {
                throw new RuntimeException("Exception while sending request", e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Exception while sending request", e);
        } finally {
            // shut down executor threads to exit
            group.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
        }
    }
}
