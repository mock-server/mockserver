package org.mockserver.proxy.http;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.filters.RequestResponseLogFilter;
import org.mockserver.proxy.Proxy;
import org.mockserver.stop.StopEventQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * This class should not be constructed directly instead use HttpProxyBuilder to build and configure this class
 *
 * @author jamesdbloom
 * @see org.mockserver.proxy.ProxyBuilder
 */
public class HttpProxy implements Proxy {

    private static final Logger logger = LoggerFactory.getLogger(HttpProxy.class);
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
                .childAttr(REQUEST_LOG_FILTER, requestLogFilter)
                .childAttr(REQUEST_RESPONSE_LOG_FILTER, requestResponseLogFilter);

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

    public List<Integer> bindToPorts(final List<Integer> requestedPortBindings) {
        List<Integer> actualPortBindings = new ArrayList<>();
        for (final Integer portToBind : requestedPortBindings) {
            try {
                final SettableFuture<Channel> channelOpened = SettableFuture.create();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        channelOpenedFutures.add(channelOpened);
                        try {

                            Channel channel =
                                    serverBootstrap
                                            .bind(portToBind)
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

                            int boundPort = ((InetSocketAddress) channelOpened.get().localAddress()).getPort();
                            proxyStarted(boundPort);
                            logger.info("MockServer started on port: {}", boundPort);

                            channel.closeFuture().syncUninterruptibly();
                        } catch (Exception e) {
                            throw new RuntimeException("Exception while binding MockServer to port " + portToBind, e.getCause());
                        }
                    }
                }, "MockServer thread for port: " + portToBind).start();

                actualPortBindings.add(((InetSocketAddress) channelOpened.get().localAddress()).getPort());
            } catch (Exception e) {
                throw new RuntimeException("Exception while binding MockServer to port " + portToBind, e.getCause());
            }
        }
        return actualPortBindings;
    }

    public Future<?> stop() {
        proxyStopping();
        return stopEventQueue.stop(this, stopping, bossGroup, workerGroup);
    }

    public HttpProxy withStopEventQueue(StopEventQueue stopEventQueue) {
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
                logger.error("Connection could not be established to proxy at socket [" + sa + "]", ioe);
            }
        };
    }

    protected void proxyStarted(Integer port) {
        ConfigurationProperties.proxyPort(port);
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", port.toString());
//        System.setProperty("https.proxyHost", "127.0.0.1");
//        System.setProperty("https.proxyPort", port.toString());
//        previousProxySelector = ProxySelector.getDefault();
//        ProxySelector.setDefault(createProxySelector("127.0.0.1", port));
    }

    protected void proxyStopping() {
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
//        System.clearProperty("https.proxyHost");
//        System.clearProperty("https.proxyPort");
//        ProxySelector.setDefault(previousProxySelector);
    }
}
