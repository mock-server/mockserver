package org.mockserver.proxy.http;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.filters.LogFilter;
import org.mockserver.proxy.Proxy;
import org.mockserver.stop.StopEventQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class should not be constructed directly instead use HttpProxyBuilder to build and configure this class
 *
 * @see org.mockserver.proxy.ProxyBuilder
 *
 * @author jamesdbloom
 */
public class HttpProxy implements Proxy {

    private static final Logger logger = LoggerFactory.getLogger(HttpProxy.class);
    private static ProxySelector previousProxySelector;
    // proxy
    private final LogFilter logFilter = new LogFilter();
    private final SettableFuture<String> hasStarted;
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
                            .childHandler(new HttpProxyUnificationHandler())
                            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                            .childAttr(HTTP_PROXY, HttpProxy.this)
                            .childAttr(HTTP_CONNECT_SOCKET, new InetSocketAddress(port))
                            .childAttr(LOG_FILTER, logFilter)
                            .bind(port)
                            .syncUninterruptibly()
                            .channel();

                    logger.info("MockServer proxy started on port: {}", ((InetSocketAddress) channel.localAddress()).getPort());

                    proxyStarted(port);
                    hasStarted.set("STARTED");

                    channel.closeFuture().syncUninterruptibly();
                } finally {
                    bossGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
                    workerGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
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
                return Arrays.asList(
                        new java.net.Proxy(java.net.Proxy.Type.SOCKS, new InetSocketAddress(host, port))
                );
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                logger.error("Connection could not be established to proxy at socket [" + sa + "]", ioe);
            }
        };
    }

    public void stop() {
        try {
            proxyStopping();
            bossGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
            workerGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
            stopEventQueue.stop();
            channel.close();
            // wait for socket to be released
            TimeUnit.MILLISECONDS.sleep(500);
        } catch (Exception ie) {
            logger.trace("Exception while stopping MockServer proxy", ie);
        }
    }

    public HttpProxy withStopEventQueue(StopEventQueue stopEventQueue) {
        this.stopEventQueue = stopEventQueue;
        this.stopEventQueue.register(this);
        return this;
    }

    public boolean isRunning() {
        if (hasStarted.isDone()) {
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                logger.trace("Exception while waiting for the proxy to confirm running status", e);
            }
            return !bossGroup.isShuttingDown() && !workerGroup.isShuttingDown();
        } else {
            return false;
        }
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
