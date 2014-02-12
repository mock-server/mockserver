package org.mockserver.mockserver;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.proxy.filters.LogFilter;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;

public class MockServerInitializer extends ChannelInitializer<SocketChannel> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final MockServerMatcher mockServerMatcher;
    private final LogFilter logFilter;
    private final MockServer server;
    private final boolean secure;

    public MockServerInitializer(MockServerMatcher mockServerMatcher, LogFilter logFilter, MockServer server, boolean secure) {
        this.mockServerMatcher = mockServerMatcher;
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
            SSLEngine engine = SSLFactory.getInstance().sslContext().createSSLEngine();
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
        pipeline.addLast("handler", new MockServerHandler(mockServerMatcher, logFilter, server, secure));
    }
}
