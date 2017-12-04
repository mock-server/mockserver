package org.mockserver.mockserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.mockserver.callback.WebSocketServerHandler;
import org.mockserver.server.netty.codec.MockServerServerCodec;
import org.mockserver.server.unification.PortUnificationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockServerInitializer extends PortUnificationHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final HttpStateHandler httpStateHandler = new HttpStateHandler();
    private final WebSocketClientRegistry webSocketClientRegistry = new WebSocketClientRegistry();
    private final MockServer mockServer;

    MockServerInitializer(MockServer mockServer) {
        this.mockServer = mockServer;
    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, ChannelPipeline pipeline) {
        // add logging
        if (logger.isDebugEnabled()) {
            pipeline.addLast(new LoggingHandler(logger));
        }

        boolean isSecure = false;
        if (ctx.channel().attr(PortUnificationHandler.SSL_ENABLED).get() != null) {
            isSecure = ctx.channel().attr(PortUnificationHandler.SSL_ENABLED).get();
        }
        pipeline.addLast(new WebSocketServerHandler(webSocketClientRegistry));
        pipeline.addLast(new MockServerServerCodec(isSecure));

        // add mock server handlers
        pipeline.addLast(new MockServerHandler(mockServer, httpStateHandler, webSocketClientRegistry));
    }
}
