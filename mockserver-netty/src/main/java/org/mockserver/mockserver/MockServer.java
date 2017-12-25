package org.mockserver.mockserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.AttributeKey;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.http.HttpProxy;

import java.util.Arrays;

import static org.mockserver.mock.HttpStateHandler.STATE_HANDLER;

/**
 * @author jamesdbloom
 */
public class MockServer extends LifeCycle<MockServer> {

    public static final AttributeKey<MockServer> MOCK_SERVER = AttributeKey.valueOf("MOCK_SERVER");

    /**
     * Start the instance using the ports provided
     *
     * @param requestedPortBindings the local port(s) to use
     */
    public MockServer(final Integer... requestedPortBindings) {
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
            .childHandler(new MockServerInitializer())
            .childAttr(MOCK_SERVER, MockServer.this)
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

}
