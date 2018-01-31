package org.mockserver.mockserver;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.mockserver.callback.CallbackWebSocketServerHandler;
import org.mockserver.client.netty.proxy.ProxyConfiguration;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.server.netty.codec.MockServerServerCodec;
import org.mockserver.ui.UIWebSocketServerHandler;
import org.mockserver.unification.PortUnificationHandler;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class MockServerUnificationInitializer extends PortUnificationHandler {

    private CallbackWebSocketServerHandler callbackWebSocketServerHandler;
    private UIWebSocketServerHandler uiWebSocketServerHandler;
    private MockServerHandler mockServerHandler;

    public MockServerUnificationInitializer(MockServer server, HttpStateHandler httpStateHandler, ProxyConfiguration proxyConfiguration) {
        super(httpStateHandler.getMockServerLogger());
        callbackWebSocketServerHandler = new CallbackWebSocketServerHandler(httpStateHandler);
        uiWebSocketServerHandler = new UIWebSocketServerHandler(httpStateHandler);
        mockServerHandler = new MockServerHandler(server, httpStateHandler, proxyConfiguration);
    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, ChannelPipeline pipeline) {
        pipeline.addLast(callbackWebSocketServerHandler);
        pipeline.addLast(uiWebSocketServerHandler);
        pipeline.addLast(new MockServerServerCodec(mockServerLogger, isSslEnabledUpstream(ctx.channel())));
        pipeline.addLast(mockServerHandler);
    }

}
