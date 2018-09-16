package org.mockserver.mockserver;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import org.mockserver.client.netty.proxy.ProxyConfiguration;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.mock.HttpStateHandler;

@ChannelHandler.Sharable
public class MockServerUnificationInitializer extends ChannelHandlerAdapter {
    private final LifeCycle server;
    private final HttpStateHandler httpStateHandler;
    private final ProxyConfiguration proxyConfiguration;

    public MockServerUnificationInitializer(LifeCycle server, HttpStateHandler httpStateHandler, ProxyConfiguration proxyConfiguration) {
        this.server = server;
        this.httpStateHandler = httpStateHandler;
        this.proxyConfiguration = proxyConfiguration;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        ctx.pipeline().replace(this, null, new MockServerUnificationInitializerDelegate(server, httpStateHandler, proxyConfiguration));
    }
}
