package org.mockserver.client.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.OutboundHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpRequest.request;

public class NettyHttpClient {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpResponse sendRequest(final OutboundHttpRequest httpRequest) {
        logger.debug("Sending request: {}", httpRequest);

        // configure the client
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            HttpClientInitializer channelInitializer = new HttpClientInitializer(httpRequest.isSecure());

            // make the connection attempt
            Channel channel = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(channelInitializer)
                    .connect(httpRequest.getHost(), httpRequest.getPort())
                    .sync()
                    .channel();

            // send the HTTP request
            channel.writeAndFlush(httpRequest);

            // wait for response
            HttpResponse httpResponse = channelInitializer.getResponseFuture().get();
            logger.debug("Received response: {}", httpResponse);

            // shutdown client
            group.shutdownGracefully(2, 15, TimeUnit.MILLISECONDS);

            return httpResponse;

        } catch (Exception e) {
            throw new RuntimeException("Exception while sending request", e);
        } finally {
            // shut down executor threads to exit
            group.shutdownGracefully(2, 15, TimeUnit.MILLISECONDS);
        }
    }
}
