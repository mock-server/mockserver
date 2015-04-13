package org.mockserver.echo.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class EchoServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger logger = LoggerFactory.getLogger(EchoServerInitializer.class);

    private final boolean secure;

    public EchoServerInitializer(boolean secure) {
        this.secure = secure;
    }

    public void initChannel(SocketChannel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();

        if (secure) {
            pipeline.addFirst(new SslHandler(SSLFactory.createServerSSLEngine()));
        }

        pipeline.addLast(new HttpServerCodec());

        pipeline.addLast(new HttpContentDecompressor());

        pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

        if (logger.isDebugEnabled()) {
            pipeline.addLast(new LoggingHandler(LogLevel.INFO));
        }

        pipeline.addLast(new EchoServerHandler());
    }
}
