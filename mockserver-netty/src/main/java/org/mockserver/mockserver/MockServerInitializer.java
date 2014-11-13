package org.mockserver.mockserver;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.filters.LogFilter;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;

public class MockServerInitializer extends ChannelInitializer<SocketChannel> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final MockServerMatcher mockServerMatcher;
    private final LogFilter logFilter;
    private final boolean secure;
    private final MockServer mockServer;

    public MockServerInitializer(MockServerMatcher mockServerMatcher, LogFilter logFilter, MockServer mockServer, boolean secure) {
        this.mockServerMatcher = mockServerMatcher;
        this.logFilter = logFilter;
        this.secure = secure;
        this.mockServer = mockServer;
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
        pipeline.addLast("decompressor", new HttpContentDecompressor());
        pipeline.addLast("chunk-aggregator", new HttpObjectAggregator(Integer.MAX_VALUE));

        // add mock server handlers
        pipeline.addLast("handler", new MockServerHandler(mockServerMatcher, logFilter, mockServer, secure));
    }
}
