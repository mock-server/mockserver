package org.mockserver.proxy.http;

import ch.qos.logback.classic.Level;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
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
public class HttpProxy implements org.mockserver.proxy.Proxy {

    private static final Logger logger = LoggerFactory.getLogger(HttpProxy.class);
    // proxy
    private final SettableFuture<String> hasStarted = SettableFuture.create();
    private ProxySelector previousProxySelector;
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
                            .bind(port)
                            .addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    if (future.isSuccess()) {
                                        hasStarted.set("STARTED");
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
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        }).start();

        try {
            hasStarted.get();
        } catch (Exception e) {
            logger.debug("Exception while waiting for proxy to complete starting up", e);
        }
    }

    public void stop() {
        try {
            proxyStopping();
            bossGroup.shutdownGracefully(1, 3, TimeUnit.MILLISECONDS);
            workerGroup.shutdownGracefully(1, 3, TimeUnit.MILLISECONDS);
            // wait for shutdown
            TimeUnit.SECONDS.sleep(1);
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

    /**
     * Override the debug WARN logging level
     *
     * @param level the log level, which can be ALL, DEBUG, INFO, WARN, ERROR, OFF
     */
    public void overrideLogLevel(String level) {
        Logger rootLogger = LoggerFactory.getLogger("org.mockserver");
        if (rootLogger instanceof ch.qos.logback.classic.Logger) {
            ((ch.qos.logback.classic.Logger) rootLogger).setLevel(Level.toLevel(level));
        }
    }

    public static ProxySelector proxySelector() {
        if (Boolean.parseBoolean(System.getProperty("defaultProxySet"))) {
            return ProxySelector.getDefault();
        } else if (Boolean.parseBoolean(System.getProperty("proxySet"))) {
            return createProxySelector(java.net.Proxy.Type.HTTP);
        } else {
            throw new IllegalStateException("ProxySelector can not be returned proxy has not been started yet");
        }
    }

    private static ProxySelector createProxySelector(final java.net.Proxy.Type http) {
        return new ProxySelector() {
            @Override
            public List<java.net.Proxy> select(URI uri) {
                return Arrays.asList(new java.net.Proxy(http, new InetSocketAddress(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort")))));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                logger.error("Connection could not be established to proxy at socket [" + sa + "]", ioe);
            }
        };
    }

    protected void proxyStarted(final Integer port, boolean socksProxy) {
        System.setProperty("proxySet", "true");
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("java.net.useSystemProxies", "true");
        System.setProperty("http.proxyPort", port.toString());
        if (socksProxy) {
            previousProxySelector = ProxySelector.getDefault();
            System.setProperty("defaultProxySet", "true");
            System.setProperty("socksProxyHost", "127.0.0.1");
            System.setProperty("socksProxyPort", port.toString());
            ProxySelector.setDefault(createProxySelector(java.net.Proxy.Type.SOCKS));
        }
    }

    protected void proxyStopping() {
        ProxySelector.setDefault(previousProxySelector);
        System.clearProperty("proxySet");
        System.clearProperty("defaultProxySet");
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("java.net.useSystemProxies");
    }
}
