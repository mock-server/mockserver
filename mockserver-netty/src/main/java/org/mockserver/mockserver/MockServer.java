package org.mockserver.mockserver;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.AttributeKey;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.stop.StopEventQueue;
import org.mockserver.stop.Stoppable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class MockServer implements Stoppable {

    private static final Logger logger = LoggerFactory.getLogger(MockServer.class);
    // mockserver
    static final AttributeKey<RequestLogFilter> REQUEST_LOG_FILTER = AttributeKey.valueOf("SERVER_LOG_FILTER");
    private final RequestLogFilter requestLogFilter = new RequestLogFilter();
    private final MockServerMatcher mockServerMatcher = new MockServerMatcher();
    private final WebSocketClientRegistry webSocketClientRegistry = new WebSocketClientRegistry();
    private final List<Future<Channel>> channelOpenedFutures = new ArrayList<Future<Channel>>();
    private final SettableFuture<String> stopping = SettableFuture.<String>create();
    // netty
    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final ServerBootstrap serverBootstrap;
    private StopEventQueue stopEventQueue = new StopEventQueue();

    /**
     * Start the instance using the port provided
     *
     * @param requestedPortBindings the http port(s) to use
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
                .childHandler(new MockServerInitializer(mockServerMatcher, MockServer.this, webSocketClientRegistry))
                .childAttr(REQUEST_LOG_FILTER, requestLogFilter);

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

    List<Integer> bindToPorts(final List<Integer> requestedPortBindings) {
        List<Integer> actualPortBindings = new ArrayList<Integer>();
        for (final Integer port : requestedPortBindings) {
            try {
                final SettableFuture<Channel> channelOpened = SettableFuture.create();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        channelOpenedFutures.add(channelOpened);
                        try {

                            Channel channel =
                                    serverBootstrap
                                            .bind(port)
                                            .addListener(new ChannelFutureListener() {
                                                @Override
                                                public void operationComplete(ChannelFuture future) throws Exception {
                                                    if (future.isSuccess()) {
                                                        channelOpened.set(future.channel());
                                                    } else {
                                                        channelOpened.setException(future.cause());
                                                    }
                                                }
                                            })
                                            .channel();

                            logger.info("MockServer started on port: {}", ((InetSocketAddress) channelOpened.get().localAddress()).getPort());

                            channel.closeFuture().syncUninterruptibly();
                        } catch (Exception e) {
                            throw new RuntimeException("Exception while binding MockServer to port " + port, e.getCause());
                        }
                    }
                }, "MockServer thread for port: " + port).start();

                actualPortBindings.add(((InetSocketAddress) channelOpened.get().localAddress()).getPort());
            } catch (Exception e) {
                throw new RuntimeException("Exception while binding MockServer to port " + port, e.getCause());
            }
        }
        return actualPortBindings;
    }

    public Future<?> stop() {
        return stopEventQueue.stop(this, stopping, bossGroup, workerGroup);
    }

    MockServer withStopEventQueue(StopEventQueue stopEventQueue) {
        this.stopEventQueue = stopEventQueue;
        this.stopEventQueue.register(this);
        return this;
    }

    public boolean isRunning() {
        return !bossGroup.isShuttingDown() || !workerGroup.isShuttingDown() || !stopping.isDone();
    }

    public List<Integer> getPorts() {
        List<Integer> ports = new ArrayList<Integer>();
        for (Future<Channel> channelOpened : channelOpenedFutures) {
            try {
                ports.add(((InetSocketAddress) channelOpened.get(2, TimeUnit.SECONDS).localAddress()).getPort());
            } catch (Exception e) {
                logger.trace("Exception while retrieving port from channel future, ignoring port for this channel", e);
            }
        }
        return ports;
    }

    public int getPort() {
        for (Future<Channel> channelOpened : channelOpenedFutures) {
            try {
                return ((InetSocketAddress) channelOpened.get(2, TimeUnit.SECONDS).localAddress()).getPort();
            } catch (Exception e) {
                logger.trace("Exception while retrieving port from channel future, ignoring port for this channel", e);
            }
        }
        return -1;
    }
}
