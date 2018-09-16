package org.mockserver.mockserver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.mockserver.callback.CallbackWebSocketServerHandler;
import org.mockserver.client.netty.proxy.ProxyConfiguration;
import org.mockserver.dashboard.DashboardWebSocketServerHandler;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.server.netty.codec.MockServerServerCodec;
import org.mockserver.unification.PortUnificationHandler;

/**
 * @author jamesdbloom
 */
public class MockServerUnificationInitializerDelegate extends PortUnificationHandler {

    private CallbackWebSocketServerHandler callbackWebSocketServerHandler;
    private DashboardWebSocketServerHandler uiWebSocketServerHandler;
    private MockServerHandler mockServerHandler;

    public MockServerUnificationInitializerDelegate(LifeCycle server, HttpStateHandler httpStateHandler, ProxyConfiguration proxyConfiguration) {
        super(server, httpStateHandler.getMockServerLogger());
        callbackWebSocketServerHandler = new CallbackWebSocketServerHandler(httpStateHandler);
        uiWebSocketServerHandler = new DashboardWebSocketServerHandler(httpStateHandler);
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
