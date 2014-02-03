package org.mockserver.proxy.http;

import ch.qos.logback.classic.Level;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socks.SocksInitRequestDecoder;
import io.netty.handler.codec.socks.SocksMessageEncoder;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.proxy.filters.LogFilter;
import org.mockserver.proxy.http.direct.DirectProxyUpstreamHandler;
import org.mockserver.proxy.interceptor.RequestInterceptor;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 */
public class HttpProxy {

    private static final Logger logger = LoggerFactory.getLogger(HttpProxy.class);
    // mockserver
    private final LogFilter logFilter = new LogFilter();
    private boolean hasBeenStarted = false;
    // jvm
    private ProxySelector previousProxySelector;
    // netty
    private EventLoopGroup bossGroup = new NioEventLoopGroup();
    private EventLoopGroup workerGroup = new NioEventLoopGroup();

    public static ProxySelector proxySelector() {
        if (Boolean.parseBoolean(System.getProperty("defaultProxySet"))) {
            return java.net.ProxySelector.getDefault();
        } else if (Boolean.parseBoolean(System.getProperty("proxySet"))) {
            return createProxySelector(Proxy.Type.HTTP);
        } else {
            throw new IllegalStateException("ProxySelector can not be returned proxy has not been started yet");
        }
    }

    private static ProxySelector createProxySelector(final Proxy.Type http) {
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return Arrays.asList(new Proxy(http, new InetSocketAddress(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort")))));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                logger.error("Connection could not be established to proxy at socket [" + sa + "]", ioe);
            }
        };
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

    /**
     * Start the instance using the ports provided
     *
     * @param port the http port to use
     * @param securePort the secure https port to use
     */
    public HttpProxy startHttpProxy(final Integer port, final Integer securePort) {
        return startProxy(port, securePort, null, null, null, null, null);
    }

    public HttpProxy startProxy(final Integer port,
                                final Integer securePort,
                                final Integer socksPort,
                                final Integer directLocalPort,
                                final Integer directLocalSecurePort,
                                final String directRemoteHost,
                                final Integer directRemotePort) {
        if (logger.isDebugEnabled()) {
            logger.debug("HTTP proxy & HTTPS CONNECT port [" + port + "]");
            logger.debug("HTTPS proxy port [" + securePort + "]");
            logger.debug("SOCKS proxy port [" + socksPort + "]");
            logger.debug("Direct proxy from port [" + directLocalPort + "] to host [" + directRemoteHost + ":" + directRemotePort + "]");
            logger.debug("Direct SSL proxy from port [" + directLocalSecurePort + "] to host [" + directRemoteHost + ":" + directRemotePort + "]");
        }

        if (port == null && securePort == null) throw new IllegalStateException("You must specify a port or a secure port");
        hasBeenStarted = true;

        final SettableFuture<String> hasConnected = SettableFuture.create();
        final SettableFuture<String> hasSecureConnected = SettableFuture.create();
        final SettableFuture<String> hasSOCKSConnected = SettableFuture.create();
        final SettableFuture<String> hasDirectConnected = SettableFuture.create();
        final SettableFuture<String> hasDirectSecureConnected = SettableFuture.create();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ChannelFuture httpChannel = null;
                    if (port != null) {

                        httpChannel = createBootstrap(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                // Create a default pipeline implementation.
                                final ChannelPipeline pipeline = ch.pipeline();

                                // add logging
                                if (logger.isDebugEnabled()) {
                                    pipeline.addLast("logger", new LoggingHandler("RAW HTTP"));
                                }

                                // add HTTP decoder and encoder
                                pipeline.addLast(HttpServerCodec.class.getSimpleName(), new HttpServerCodec());

                                // add handler
                                pipeline.addLast(HttpProxyHandler.class.getSimpleName(), new HttpProxyHandler(logFilter, HttpProxy.this, securePort != null ? new InetSocketAddress(securePort) : null, false));
                            }
                        }, port, true, hasConnected);

                        // create system wide proxy settings (this covers HTTP and HTTPS CONNECT)
                        proxyStarted(port, false);
                    } else {
                        hasConnected.set("NOT CONNECTED");
                    }
                    ChannelFuture httpsChannel = null;
                    if (securePort != null) {

                        httpsChannel = createBootstrap(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                // Create a default pipeline implementation.
                                final ChannelPipeline pipeline = ch.pipeline();

                                // add HTTPS support
                                SSLEngine engine = SSLFactory.sslContext().createSSLEngine();
                                engine.setUseClientMode(false);
                                pipeline.addLast(SslHandler.class.getSimpleName(), new SslHandler(engine));

                                // add logging
                                if (logger.isDebugEnabled()) {
                                    pipeline.addLast("logger", new LoggingHandler("RAW HTTPS"));
                                }

                                // add HTTP decoder and encoder
                                pipeline.addLast(HttpServerCodec.class.getSimpleName(), new HttpServerCodec());

                                // add handler
                                pipeline.addLast(HttpProxyHandler.class.getSimpleName(), new HttpProxyHandler(logFilter, HttpProxy.this, new InetSocketAddress(securePort), true));
                            }
                        }, securePort, true, hasSecureConnected);
                    } else {
                        hasSecureConnected.set("NOT CONNECTED");
                    }
                    ChannelFuture socksChannel = null;
                    if (socksPort != null && port != null) {
                        socksChannel = createBootstrap(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                // Create a default pipeline implementation.
                                final ChannelPipeline pipeline = ch.pipeline();

                                // add logging
                                if (logger.isDebugEnabled()) {
                                    pipeline.addLast("logger", new LoggingHandler("RAW SOCKS"));
                                }

                                // add SOCKS decoder and encoder
                                pipeline.addLast(SocksInitRequestDecoder.class.getSimpleName(), new SocksInitRequestDecoder());
                                pipeline.addLast(SocksMessageEncoder.class.getSimpleName(), new SocksMessageEncoder());

                                // add handler
                                pipeline.addLast(HttpProxyHandler.class.getSimpleName(), new HttpProxyHandler(logFilter, HttpProxy.this, new InetSocketAddress(port), false));
                            }
                        }, socksPort, true, hasSOCKSConnected);

                        // create system wide proxy settings (this covers SOCKS - assuming if SOCK port is set this overrides HTTP and HTTPS CONNECT)
                        proxyStarted(socksPort, true);
                    } else {
                        hasSOCKSConnected.set("NOT CONNECTED");
                    }
                    ChannelFuture directChannel = null;
                    if (directLocalPort != null && directRemoteHost != null && directRemotePort != null) {
                        directChannel = createBootstrap(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                // Create a default pipeline implementation.
                                ChannelPipeline pipeline = ch.pipeline();

                                // add logging
                                if (logger.isDebugEnabled()) {
                                    pipeline.addLast("logger", new LoggingHandler("RAW DIRECT HTTP"));
                                }

                                // add handler
                                pipeline.addLast(new DirectProxyUpstreamHandler(new InetSocketAddress(directRemoteHost, directRemotePort), false, 1048576, new RequestInterceptor(), "<--- "));
                            }
                        }, directLocalPort, false, hasDirectConnected);
                    } else {
                        hasDirectConnected.set("NOT CONNECTED");
                    }
                    ChannelFuture directSecureChannel = null;
                    if (directLocalSecurePort != null && directRemoteHost != null && directRemotePort != null) {

                        directSecureChannel = createBootstrap(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                // Create a default pipeline implementation.
                                ChannelPipeline pipeline = ch.pipeline();

                                // add HTTPS support
                                SSLEngine engine = SSLFactory.sslContext().createSSLEngine();
                                engine.setUseClientMode(false);
                                pipeline.addLast("ssl", new SslHandler(engine));

                                // add logging
                                if (logger.isDebugEnabled()) {
                                    pipeline.addLast("logger", new LoggingHandler("RAW DIRECT HTTPS"));
                                }

                                // add handler
                                pipeline.addLast(new DirectProxyUpstreamHandler(new InetSocketAddress(directRemoteHost, directRemotePort), true, 1048576, new RequestInterceptor(), "<--- "));
                            }
                        }, directLocalSecurePort, false, hasDirectSecureConnected);
                    } else {
                        hasDirectSecureConnected.set("NOT CONNECTED");
                    }
                    if (httpChannel != null) {
                        httpChannel.channel().closeFuture().sync();
                    }
                    if (httpsChannel != null) {
                        httpsChannel.channel().closeFuture().sync();
                    }
                    if (socksChannel != null) {
                        socksChannel.channel().closeFuture().sync();
                    }
                    if (directChannel != null) {
                        directChannel.channel().closeFuture().sync();
                    }
                    if (directSecureChannel != null) {
                        directSecureChannel.channel().closeFuture().sync();
                    }
                } catch (InterruptedException ie) {
                    logger.error("Proxy receive InterruptedException", ie);
                } finally {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        }).start();

        try {
            // wait for connection
            hasConnected.get();
            hasSecureConnected.get();
            hasSOCKSConnected.get();
            hasDirectConnected.get();
            hasDirectSecureConnected.get();
        } catch (Exception e) {
            logger.debug("Exception while waiting for proxy to complete starting up", e);
        }
        return this;
    }

    private ChannelFuture createBootstrap(final ChannelInitializer<SocketChannel> childHandler, final Integer port, boolean autoRead, final SettableFuture<String> hasConnected) throws InterruptedException {
        return new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(childHandler)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.AUTO_READ, autoRead)
                .bind(port).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            hasConnected.set("CONNECTED");
                        } else {
                            hasConnected.setException(future.cause());
                        }
                    }
                });
    }

    protected void proxyStarted(final Integer port, boolean socksProxy) {
        System.setProperty("proxySet", "true");
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("java.net.useSystemProxies", "true");
        System.setProperty("http.proxyPort", port.toString());
        if (socksProxy) {
            previousProxySelector = java.net.ProxySelector.getDefault();
            System.setProperty("defaultProxySet", "true");
            java.net.ProxySelector.setDefault(createProxySelector(Proxy.Type.SOCKS));
        }
    }

    protected void proxyStopping() {
        java.net.ProxySelector.setDefault(previousProxySelector);
        System.clearProperty("proxySet");
        System.clearProperty("defaultProxySet");
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("java.net.useSystemProxies");
    }

    public void stop() {
        try {
            workerGroup.shutdownGracefully(2, 15, TimeUnit.SECONDS);
            bossGroup.shutdownGracefully(2, 15, TimeUnit.SECONDS);
        } catch (Exception ie) {
            logger.trace("Exception while waiting for MockServer to stop", ie);
        }
    }

    public boolean isRunning() {
        if (hasBeenStarted) {
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return !bossGroup.isShuttingDown() && !workerGroup.isShuttingDown();
        } else {
            return false;
        }
    }
}
