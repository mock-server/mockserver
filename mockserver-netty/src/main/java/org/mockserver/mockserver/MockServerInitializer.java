package org.mockserver.mockserver;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;

public class MockServerInitializer extends ChannelInitializer<SocketChannel> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final boolean secure;
    private final MockServerHandler mockServerHandler;

    public MockServerInitializer(MockServerHandler mockServerHandler, boolean secure) {
        this.secure = secure;
        this.mockServerHandler = mockServerHandler;
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
        pipeline.addLast("chunk-aggregator", new HttpObjectAggregator(10 * 1024 * 1024));

        // add mock server handlers
        pipeline.addLast("mock-server-request-codec", new MockServerHttpRequestCodec(secure));
        pipeline.addLast("handler", mockServerHandler);
    }
}
