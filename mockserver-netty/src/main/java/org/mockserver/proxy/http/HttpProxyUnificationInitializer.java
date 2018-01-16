package org.mockserver.proxy.http;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.mockserver.callback.CallbackWebSocketServerHandler;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.proxy.Proxy;
import org.mockserver.server.netty.codec.MockServerServerCodec;
import org.mockserver.ui.UIWebSocketServerHandler;
import org.mockserver.unification.PortUnificationHandler;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class HttpProxyUnificationInitializer extends PortUnificationHandler {

    private CallbackWebSocketServerHandler callbackWebSocketServerHandler;
    private UIWebSocketServerHandler uiWebSocketServerHandler;
    private HttpProxyHandler httpProxyHandler;

    public HttpProxyUnificationInitializer(Proxy server, HttpStateHandler httpStateHandler) {
        super(httpStateHandler.getMockServerLogger());
        callbackWebSocketServerHandler = new CallbackWebSocketServerHandler(httpStateHandler);
        uiWebSocketServerHandler = new UIWebSocketServerHandler(httpStateHandler);
        httpProxyHandler = new HttpProxyHandler(server, httpStateHandler);
    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, ChannelPipeline pipeline) {
        pipeline.addLast(callbackWebSocketServerHandler);
        pipeline.addLast(uiWebSocketServerHandler);
        pipeline.addLast(new MockServerServerCodec(mockServerLogger, isSslEnabledUpstream(ctx.channel())));
        pipeline.addLast(httpProxyHandler);
    }

}
