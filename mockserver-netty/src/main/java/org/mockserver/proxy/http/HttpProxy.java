package org.mockserver.proxy.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.proxy.Proxy;

import java.net.InetSocketAddress;
import java.util.Arrays;

/**
 * @author jamesdbloom
 */
public class HttpProxy extends Proxy<HttpProxy> {

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
            .childHandler(new HttpProxyUnificationInitializer(HttpProxy.this, httpStateHandler))
            .childAttr(HTTP_CONNECT_SOCKET, new InetSocketAddress(requestedPortBindings[0]));

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

    protected void started(Integer port) {
        super.started(port);
        ConfigurationProperties.proxyPort(port);
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", port.toString());
    }

    protected void stopped() {
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
    }
}
