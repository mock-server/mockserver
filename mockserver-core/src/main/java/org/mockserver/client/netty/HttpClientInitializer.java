package org.mockserver.client.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import org.mockserver.client.netty.codec.MockServerClientCodec;
import org.mockserver.client.netty.proxy.ProxyConfiguration;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.logging.MockServerLogger;

import java.net.InetSocketAddress;

import static org.mockserver.socket.NettySslContextFactory.nettySslContextFactory;
import static org.slf4j.event.Level.TRACE;

@ChannelHandler.Sharable
public class HttpClientInitializer extends ChannelInitializer<SocketChannel> {

    private final MockServerLogger mockServerLogger = new MockServerLogger(this.getClass());
    private final HttpClientConnectionHandler httpClientConnectionHandler = new HttpClientConnectionHandler();
    private final HttpClientHandler httpClientHandler = new HttpClientHandler();
    private final ProxyConfiguration proxyConfiguration;
    private final NettyHttpClient.ChannelPoolSelector channelPoolSelector;

    public HttpClientInitializer(ProxyConfiguration proxyConfiguration, NettyHttpClient.ChannelPoolSelector channelPoolSelector) {
        this.proxyConfiguration = proxyConfiguration;
        this.channelPoolSelector = channelPoolSelector;
    }

    @Override
    public void initChannel(SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();

        if (proxyConfiguration != null) {
            if (proxyConfiguration.getType() == ProxyConfiguration.Type.HTTPS) {
                pipeline.addLast(new HttpProxyHandler(proxyConfiguration.getProxyAddress()));
            } else if (proxyConfiguration.getType() == ProxyConfiguration.Type.SOCKS5) {
                pipeline.addLast(new Socks5ProxyHandler(proxyConfiguration.getProxyAddress()));
            }
        }
        pipeline.addLast(httpClientConnectionHandler);

        if (channelPoolSelector.isSecure) {
            pipeline.addLast(nettySslContextFactory().createClientSslContext().newHandler(channel.alloc(), channelPoolSelector.remoteAddress.getHostName(), channelPoolSelector.remoteAddress.getPort()));
        }

        // add logging
        if (mockServerLogger.isEnabled(TRACE)) {
            pipeline.addLast(new LoggingHandler("NettyHttpClient -->"));
        }

        pipeline.addLast(new HttpClientCodec());

        pipeline.addLast(new HttpContentDecompressor());

        pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

        pipeline.addLast(new MockServerClientCodec());

        pipeline.addLast(httpClientHandler);
    }
}
