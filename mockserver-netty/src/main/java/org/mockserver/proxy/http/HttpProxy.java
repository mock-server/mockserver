package org.mockserver.proxy.http;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mockserver.filters.LogFilter;
import org.mockserver.proxy.Proxy;
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
    // proxy
    private final SettableFuture<String> hasStarted = SettableFuture.create();
    private final LogFilter logFilter = new LogFilter();
    // netty
    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    // ports
    private final Integer port;

    public HttpProxy(final Integer port) {

        if (port == null) {
            throw new IllegalArgumentException("Port must not be null");
        }

        this.port = port;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    new ServerBootstrap()
                            .group(bossGroup, workerGroup)
                            .option(ChannelOption.SO_BACKLOG, 1024)
                            .channel(NioServerSocketChannel.class)
                            .childOption(ChannelOption.AUTO_READ, true)
                            .childHandler(new HttpProxyUnificationHandler())
                            .childAttr(HTTP_PROXY, HttpProxy.this)
                            .childAttr(REMOTE_SOCKET, new InetSocketAddress(port))
                            .childAttr(LOG_FILTER, logFilter)
                            .bind(port)
                            .addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    if (future.isSuccess()) {
                                        hasStarted.set("STARTED");
                                        proxyStarted(port);
                                    } else {
                                        hasStarted.setException(future.cause());
                                    }
                                }
                            })
                            .channel()
                            .closeFuture()
                            .sync();
                } catch (Exception ie) {
                    logger.error("Exception while running proxy channels", ie);
                } finally {
                    bossGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
                    workerGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
                }
            }
        }).start();

        try {
            hasStarted.get();
        } catch (Exception e) {
            logger.debug("Exception while waiting for proxy to complete starting up", e);
        }
    }

    private static ProxySelector createProxySelector(final String host, final int port) {
        return new ProxySelector() {
            @Override
            public List<java.net.Proxy> select(URI uri) {
                return Arrays.asList(
                        new java.net.Proxy(java.net.Proxy.Type.SOCKS, new InetSocketAddress(host, port)),
                        new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(host, port))
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
            // wait for socket to be released
            TimeUnit.MILLISECONDS.sleep(500);
        } catch (Exception ie) {
            logger.trace("Exception while waiting for the proxy to stop", ie);
        }
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
        return port;
    }

    protected void proxyStarted(Integer port) {
        System.setProperty("proxySet", "true");
        System.setProperty("socksProxyHost", "127.0.0.1");
        System.setProperty("socksProxyPort", port.toString());
        System.setProperty("socksProxyVersion", "5");
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", port.toString());
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", port.toString());
    }

    protected void proxyStopping() {
        System.clearProperty("proxySet");
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
    }
}
