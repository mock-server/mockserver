package org.mockserver.proxy.http;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

/**
 * This class should not be constructed directly instead use HttpProxyBuilder to build and configure this class
 *
 * @author jamesdbloom
 * @see org.mockserver.proxy.ProxyBuilder
 */
public class HttpProxy implements Proxy {

    private static final Logger logger = LoggerFactory.getLogger(HttpProxy.class);
    private static ProxySelector previousProxySelector;
    // proxy
    private final RequestLogFilter requestLogFilter = new RequestLogFilter();
    private final RequestResponseLogFilter requestResponseLogFilter = new RequestResponseLogFilter();
    private final SettableFuture<String> hasStarted;
    private final SettableFuture<String> stopping = SettableFuture.<String>create();
    // netty
    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private StopEventQueue stopEventQueue = new StopEventQueue();
    private Channel channel;

    /**
     * Start the instance using the ports provided
     *
     * @param port the http port to use
     */
    public HttpProxy(final Integer port) {
        if (port == null) {
            throw new IllegalArgumentException("You must specify a port");
        }

        hasStarted = SettableFuture.create();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    channel = new ServerBootstrap()
                            .group(bossGroup, workerGroup)
                            .option(ChannelOption.SO_BACKLOG, 1024)
                            .channel(NioServerSocketChannel.class)
                            .childOption(ChannelOption.AUTO_READ, true)
                            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                            .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
                            .childHandler(new HttpProxyUnificationHandler())
                            .childAttr(HTTP_PROXY, HttpProxy.this)
                            .childAttr(HTTP_CONNECT_SOCKET, new InetSocketAddress(port))
                            .childAttr(REQUEST_LOG_FILTER, requestLogFilter)
                            .childAttr(REQUEST_RESPONSE_LOG_FILTER, requestResponseLogFilter)
                            .bind(port)
                            .syncUninterruptibly()
                            .channel();

                    logger.info("MockServer proxy started on port: {}", ((InetSocketAddress) channel.localAddress()).getPort());

                    proxyStarted(port);
                    hasStarted.set("STARTED");

                    channel.closeFuture().syncUninterruptibly();
                } finally {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        }, "MockServer HttpProxy Thread").start();

        try {
            hasStarted.get();
        } catch (Exception e) {
            logger.warn("Exception while waiting for MockServer proxy to complete starting up", e);
        }
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

    public Integer getPort() {
        return ((InetSocketAddress) channel.localAddress()).getPort();
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
