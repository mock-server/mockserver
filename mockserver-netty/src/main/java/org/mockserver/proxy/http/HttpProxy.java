package org.mockserver.proxy.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.proxy.Proxy;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This class should not be constructed directly instead use HttpProxyBuilder to build and configure this class
 *
 * @author jamesdbloom
 * @see org.mockserver.proxy.ProxyBuilder
 */
public class HttpProxy extends Proxy<HttpProxy> {

    private ProxySelector previousProxySelector;

    /**
     * Start the instance using the ports provided
     *
     * @param requestedPortBindings the local port(s) to use
     */
    public HttpProxy(final Integer... requestedPortBindings) {
        if (requestedPortBindings == null || requestedPortBindings.length == 0) {
            throw new IllegalArgumentException("You must specify at least one port");
        }

        serverBootstrap = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .option(ChannelOption.SO_BACKLOG, 1024)
            .channel(NioServerSocketChannel.class)
            .childOption(ChannelOption.AUTO_READ, true)
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
            .childHandler(new HttpProxyUnificationHandler())
            .childAttr(HTTP_PROXY, HttpProxy.this)
            .childAttr(HTTP_CONNECT_SOCKET, new InetSocketAddress(requestedPortBindings[0]))
            .childAttr(STATE_HANDLER, new HttpStateHandler());

        bindToPorts(Arrays.asList(requestedPortBindings));

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                // Shut down all event loops to terminate all threads.
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();

                // Wait until all threads are terminated.
                try {
                    bossGroup.terminationFuture().sync();
                    workerGroup.terminationFuture().sync();
                } catch (InterruptedException e) {
                    // ignore interrupted exceptions
                }
            }
        }));
    }

    private static ProxySelector createProxySelector(final String host, final int port) {
        return new ProxySelector() {
            @Override
            public List<java.net.Proxy> select(URI uri) {
                return Collections.singletonList(
                    new java.net.Proxy(java.net.Proxy.Type.SOCKS, new InetSocketAddress(host, port))
                );
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                LoggerFactory.getLogger(HttpProxy.class).error("Connection could not be established to proxy at socket [" + sa + "]", ioe);
            }
        };
    }

    protected void started(Integer port) {
        ConfigurationProperties.proxyPort(port);
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", port.toString());
//        previousProxySelector = ProxySelector.getDefault();
//        ProxySelector.setDefault(createProxySelector("127.0.0.1", port));
    }

    protected void stopped() {
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
//        ProxySelector.setDefault(previousProxySelector);
    }
}
