package org.mockserver.mockserver;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.mock.MockServer;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.proxy.filters.LogFilter;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;

public class MockServerInitializer extends ChannelInitializer<SocketChannel> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final MockServer mockServer;
    private final LogFilter logFilter;
    private final NettyMockServer server;
    private final boolean secure;

    public MockServerInitializer(MockServer mockServer, LogFilter logFilter, NettyMockServer server, boolean secure) {
        this.mockServer = mockServer;
        this.logFilter = logFilter;
        this.server = server;
        this.secure = secure;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = ch.pipeline();

        // add HTTPS support
        if (secure) {
            SSLEngine engine = SSLFactory.sslContext().createSSLEngine();
            engine.setUseClientMode(false);
            pipeline.addLast("ssl", new SslHandler(engine));
        }

        // add logging
        if (logger.isDebugEnabled()) {
            pipeline.addLast("logger", new LoggingHandler());
        }

        // add msg <-> HTTP
        pipeline.addLast("decoder-encoder", new HttpServerCodec());

        // add handler
        pipeline.addLast("handler", new MockServerHandler(mockServer, logFilter, server, secure));
    }
}
