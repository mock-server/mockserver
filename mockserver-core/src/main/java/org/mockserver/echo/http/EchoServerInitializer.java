package org.mockserver.echo.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.server.netty.codec.MockServerServerCodec;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockserver.echo.http.EchoServer.LOG_FILTER;
import static org.mockserver.echo.http.EchoServer.NEXT_RESPONSE;

/**
 * @author jamesdbloom
 */
public class EchoServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger logger = LoggerFactory.getLogger(EchoServerInitializer.class);

    private final boolean secure;
    private final EchoServer.Error error;

    public EchoServerInitializer(boolean secure, EchoServer.Error error) {
        if (!secure && error == EchoServer.Error.CLOSE_CONNECTION) {
            throw new IllegalArgumentException("Error type CLOSE_CONNECTION is not supported in non-secure mode");
        }
        this.secure = secure;
        this.error = error;
    }

    public void initChannel(SocketChannel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();

        if (logger.isDebugEnabled()) {
            pipeline.addLast(new LoggingHandler(logger));
        }

        if (error != null) {
            pipeline.addLast(new ErrorHandler(error));
        }

        if (secure) {
            pipeline.addLast(new SslHandler(SSLFactory.createServerSSLEngine()));
        }

        pipeline.addLast(new HttpServerCodec());

        pipeline.addLast(new HttpContentDecompressor());

        pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

        pipeline.addLast(new MockServerServerCodec(secure));

        pipeline.addLast(new EchoServerHandler(error, secure, channel.attr(LOG_FILTER).get(), channel.attr(NEXT_RESPONSE).get()));
    }
}
