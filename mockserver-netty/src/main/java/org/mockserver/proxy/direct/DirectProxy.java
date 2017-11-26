package org.mockserver.proxy.direct;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.filters.RequestResponseLogFilter;
import org.mockserver.proxy.Proxy;
import org.mockserver.stop.StopEventQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * This class should not be constructed directly instead use HttpProxyBuilder to build and configure this class
 *
 * @author jamesdbloom
 * @see org.mockserver.proxy.ProxyBuilder
 */
public class DirectProxy implements Proxy {

    private static final Logger logger = LoggerFactory.getLogger(DirectProxy.class);
    // proxy
    private final RequestLogFilter requestLogFilter = new RequestLogFilter();
    private final RequestResponseLogFilter requestResponseLogFilter = new RequestResponseLogFilter();
    private final List<Future<Channel>> channelOpenedFutures = new ArrayList<Future<Channel>>();
    private final SettableFuture<String> stopping = SettableFuture.<String>create();
    // netty
    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final ServerBootstrap serverBootstrap;
    private StopEventQueue stopEventQueue = new StopEventQueue();

    // remote socket
    private InetSocketAddress remoteSocket;

    /**
     * Start the instance using the ports provided
     *
     * @param localPorts the local port(s) to use
     * @param remoteHost the hostname of the remote server to connect to
     * @param remotePort the port of the remote server to connect to
     */
    public DirectProxy(final String remoteHost, final Integer remotePort, final Integer... localPorts) {
        if (remoteHost == null) {
            throw new IllegalArgumentException("You must specify a remote port");
        }
        if (remotePort == null) {
            throw new IllegalArgumentException("You must specify a remote hostname");
        }
        if (localPorts == null || localPorts.length == 0) {
            throw new IllegalArgumentException("You must specify at least one port");
        }

        remoteSocket = new InetSocketAddress(remoteHost, remotePort);
        serverBootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.AUTO_READ, true)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
                .childHandler(new DirectProxyUnificationHandler())
                .childAttr(HTTP_PROXY, DirectProxy.this)
                .childAttr(REMOTE_SOCKET, remoteSocket)
                .childAttr(REQUEST_LOG_FILTER, requestLogFilter)
                .childAttr(REQUEST_RESPONSE_LOG_FILTER, requestResponseLogFilter);

        bindToPorts(Arrays.asList(localPorts));

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

    public List<Integer> bindToPorts(final List<Integer> requestedPortBindings) {
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

    public DirectProxy withStopEventQueue(StopEventQueue stopEventQueue) {
        this.stopEventQueue = stopEventQueue;
        this.stopEventQueue.register(this);
        return this;
    }

    public boolean isRunning() {
        return !bossGroup.isShuttingDown() || !workerGroup.isShuttingDown() || !stopping.isDone();
    }

    public List<Integer> getPorts() {
        List<Integer> ports = new ArrayList<>();
        for (Future<Channel> channelOpened : channelOpenedFutures) {
            try {
                ports.add(((InetSocketAddress) channelOpened.get(2, TimeUnit.SECONDS).localAddress()).getPort());
            } catch (Exception e) {
                logger.trace("Exception while retrieving port from channel future, ignoring port for this channel", e);
            }
        }
        return ports;
    }

    public int getLocalPort() {
        for (Future<Channel> channelOpened : channelOpenedFutures) {
            try {
                return ((InetSocketAddress) channelOpened.get(2, TimeUnit.SECONDS).localAddress()).getPort();
            } catch (Exception e) {
                logger.trace("Exception while retrieving port from channel future, ignoring port for this channel", e);
            }
        }
        return -1;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteSocket;
    }

}
