package org.mockserver.mockserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mockserver.client.netty.proxy.ProxyConfiguration;
import org.mockserver.lifecycle.LifeCycle;

import java.util.Arrays;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.mockserver.client.netty.proxy.ProxyConfiguration.proxyConfiguration;

/**
 * @author jamesdbloom
 */
public class MockServer extends LifeCycle<MockServer> {

    /**
     * Start the instance using the ports provided
     *
     * @param requestedPortBindings the local port(s) to use, use 0 or no vararg values to specify any free port
     */
    public MockServer(final Integer... requestedPortBindings) {
        this(proxyConfiguration(), requestedPortBindings);
    }

    /**
     * Start the instance using the ports provided configuring forwarded or proxied requests to go via an additional proxy
     *
     * @param proxyConfiguration    the proxy configuration for forwarded or proxied requests
     * @param requestedPortBindings the local port(s) to use, use 0 or no vararg values to specify any free port
     */
    public MockServer(ProxyConfiguration proxyConfiguration, final Integer... requestedPortBindings) {
        List<Integer> portBindings = singletonList(0);
        if (requestedPortBindings != null && requestedPortBindings.length > 0) {
            portBindings = Arrays.asList(requestedPortBindings);
        }

        serverBootstrap = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .option(ChannelOption.SO_BACKLOG, 1024)
            .channel(NioServerSocketChannel.class)
            .childOption(ChannelOption.AUTO_READ, true)
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
            .childHandler(new MockServerUnificationInitializer(MockServer.this, httpStateHandler, proxyConfiguration));

        bindToPorts(portBindings);

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

}
