package org.mockserver.client.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import org.mockserver.client.netty.codec.MockServerClientCodec;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.logging.MockServerLogger;

import java.net.InetSocketAddress;

import static org.mockserver.client.netty.NettyHttpClient.REMOTE_SOCKET;
import static org.mockserver.client.netty.NettyHttpClient.SECURE;
import static org.mockserver.socket.NettySslContextFactory.nettySslContextFactory;
import static org.slf4j.event.Level.TRACE;

@ChannelHandler.Sharable
public class HttpClientInitializer extends ChannelInitializer<SocketChannel> {

    private final MockServerLogger mockServerLogger = new MockServerLogger(this.getClass());
    private final HttpClientConnectionHandler httpClientConnectionHandler = new HttpClientConnectionHandler();
    private final HttpClientHandler httpClientHandler = new HttpClientHandler();

    @Override
    public void initChannel(SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();

        pipeline.addLast(httpClientConnectionHandler);

        if (channel.attr(SECURE) != null && channel.attr(SECURE).get() != null && channel.attr(SECURE).get()) {
            InetSocketAddress remoteAddress = channel.attr(REMOTE_SOCKET).get();
            pipeline.addLast(nettySslContextFactory().createClientSslContext().newHandler(channel.alloc(), remoteAddress.getHostName(), remoteAddress.getPort()));
        }

        // add logging
        if (mockServerLogger.isEnabled(TRACE)) {
            pipeline.addLast(new LoggingHandler("NettyHttpClient -->"));
        }

        pipeline.addLast(new HttpClientCodec());

        pipeline.addLast(new HttpContentDecompressor());

        pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

        pipeline.addLast(new MockServerClientCodec());

        pipeline.addLast(httpClientHandler);
    }
}
