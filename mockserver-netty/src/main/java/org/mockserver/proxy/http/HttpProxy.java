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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * This class should not be constructed directly instead use HttpProxyBuilder to build and configure this class
 *
 * @see org.mockserver.proxy.http.HttpProxyBuilder
 *
 * @author jamesdbloom
 */
public class HttpProxy {

    private static final Logger logger = LoggerFactory.getLogger(HttpProxy.class);
    // mockserver
    private final LogFilter logFilter = new LogFilter();
    private SettableFuture<String> hasStarted;
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
     * Start the instance using the ports provided, this method should not be used directly, instead use HttpProxyBuilder
     *
     * @param port the HTTP port to use
     * @param securePort the HTTP/SSL (HTTPS) port to use
     * @param socksPort the SOCKS port to use
     * @param directLocalPort the local proxy port for direct forwarding
     * @param directLocalSecurePort the local proxy port for direct forwarding over SSL
     * @param directRemoteHost the destination hostname for direct forwarding
     * @param directRemotePort the destination port for direct forwarding
     *
     * @see org.mockserver.proxy.http.HttpProxyBuilder
     */
    Thread start(final Integer port,
                 final Integer securePort,
                 final Integer socksPort,
                 final Integer directLocalPort,
                 final Integer directLocalSecurePort,
                 final String directRemoteHost,
                 final Integer directRemotePort) {


        hasStarted = SettableFuture.create();

        Thread proxyThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ChannelFuture httpChannel = createHTTPChannel(port, securePort);
                    ChannelFuture httpsChannel = createHTTPSChannel(securePort);
                    ChannelFuture socksChannel = createSOCKSChannel(socksPort, port);
                    ChannelFuture directChannel = createDirectChannel(directLocalPort, directRemoteHost, directRemotePort);
                    ChannelFuture directSecureChannel = createDirectSecureChannel(directLocalSecurePort, directRemoteHost, directRemotePort);

                    if (httpChannel != null) {
                        // create system wide proxy settings for HTTP CONNECT
                        proxyStarted(port, false);
                    }
                    if (socksChannel != null) {
                        // create system wide proxy settings for SOCKS
                        proxyStarted(socksPort, true);
                    }
                    hasStarted.set("STARTED");

                    waitForClose(httpChannel);
                    waitForClose(httpsChannel);
                    waitForClose(socksChannel);
                    waitForClose(directChannel);
                    waitForClose(directSecureChannel);
                } catch (Exception ie) {
                    logger.error("Exception while running proxy channels", ie);
                } finally {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        });
        proxyThread.start();

        try {
            // wait for proxy to start all channels
            hasStarted.get();
        } catch (Exception e) {
            logger.debug("Exception while waiting for proxy to complete starting up", e);
        }

        return proxyThread;
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

    private void waitForClose(ChannelFuture httpChannel) throws InterruptedException {
        if (httpChannel != null) {
            httpChannel.channel().closeFuture().sync();
        }
    }

    private ChannelFuture createHTTPChannel(final Integer port, final Integer securePort) throws ExecutionException, InterruptedException {
        boolean condition = port != null;
        if (condition) logger.info("Starting HTTP proxy & HTTPS CONNECT port [" + port + "]");
        return createBootstrap(condition, new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                // Create a default pipeline implementation.
                ChannelPipeline pipeline = ch.pipeline();

                // add HTTP decoder and encoder
                pipeline.addLast(HttpServerCodec.class.getSimpleName(), new HttpServerCodec());

                // add handler
                pipeline.addLast(HttpProxyHandler.class.getSimpleName(), new HttpProxyHandler(logFilter, HttpProxy.this, securePort != null ? new InetSocketAddress(securePort) : null, false));
            }
        }, port, true);
    }

    private ChannelFuture createHTTPSChannel(final Integer securePort) throws ExecutionException, InterruptedException {
        boolean condition = securePort != null;
        if (condition) logger.info("Starting HTTPS proxy port [" + securePort + "]");
        return createBootstrap(condition, new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                // Create a default pipeline implementation.
                ChannelPipeline pipeline = ch.pipeline();

                // add HTTPS support
                SSLEngine engine = SSLFactory.getInstance().sslContext().createSSLEngine();
                engine.setUseClientMode(false);
                pipeline.addLast(SslHandler.class.getSimpleName(), new SslHandler(engine));

                // add HTTP decoder and encoder
                pipeline.addLast(HttpServerCodec.class.getSimpleName(), new HttpServerCodec());

                // add handler
                pipeline.addLast(HttpProxyHandler.class.getSimpleName(), new HttpProxyHandler(logFilter, HttpProxy.this, securePort != null ? new InetSocketAddress(securePort) : null, true));
            }
        }, securePort, true);
    }

    private ChannelFuture createSOCKSChannel(final Integer socksPort, final Integer port) throws ExecutionException, InterruptedException {
        boolean condition = socksPort != null && port != null;
        if (condition) logger.info("Starting SOCKS proxy port [" + socksPort + "]");
        return createBootstrap(condition, new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                // Create a default pipeline implementation.
                ChannelPipeline pipeline = ch.pipeline();

                // add SOCKS decoder and encoder
                pipeline.addLast(SocksInitRequestDecoder.class.getSimpleName(), new SocksInitRequestDecoder());
                pipeline.addLast(SocksMessageEncoder.class.getSimpleName(), new SocksMessageEncoder());

                // add handler
                pipeline.addLast(HttpProxyHandler.class.getSimpleName(), new HttpProxyHandler(logFilter, HttpProxy.this, new InetSocketAddress(port), false));
            }
        }, socksPort, true);
    }

    private ChannelFuture createDirectChannel(final Integer directLocalPort, final String directRemoteHost, final Integer directRemotePort) throws ExecutionException, InterruptedException {
        boolean condition = directLocalPort != null && directRemoteHost != null && directRemotePort != null;
        if (condition) logger.info("Starting Direct proxy from port [" + directLocalPort + "] to host [" + directRemoteHost + ":" + directRemotePort + "]");
        return createBootstrap(condition, new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                // Create a default pipeline implementation.
                ChannelPipeline pipeline = ch.pipeline();

                // add handler
                InetSocketAddress remoteSocketAddress = new InetSocketAddress(directRemoteHost, directRemotePort);
                pipeline.addLast(new DirectProxyUpstreamHandler(remoteSocketAddress, false, 1048576, new RequestInterceptor(remoteSocketAddress), "                -->"));
            }
        }, directLocalPort, false);
    }

    private ChannelFuture createDirectSecureChannel(final Integer directLocalSecurePort, final String directRemoteHost, final Integer directRemotePort) throws ExecutionException, InterruptedException {
        boolean condition = directLocalSecurePort != null && directRemoteHost != null && directRemotePort != null;
        if (condition) logger.info("Starting Direct SSL proxy from port [" + directLocalSecurePort + "] to host [" + directRemoteHost + ":" + directRemotePort + "]");
        return createBootstrap(condition, new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                // Create a default pipeline implementation.
                ChannelPipeline pipeline = ch.pipeline();

                // add HTTPS client -> proxy support
                SSLEngine engine = SSLFactory.getInstance().sslContext().createSSLEngine();
                engine.setUseClientMode(false);
                pipeline.addLast("ssl inbound", new SslHandler(engine));

                // add handler
                InetSocketAddress remoteSocketAddress = new InetSocketAddress(directRemoteHost, directRemotePort);
                pipeline.addLast(new DirectProxyUpstreamHandler(remoteSocketAddress, true, 1048576, new RequestInterceptor(remoteSocketAddress), "                -->"));

            }
        }, directLocalSecurePort, false);
    }

    private ChannelFuture createBootstrap(boolean condition, final ChannelInitializer<SocketChannel> childHandler, final Integer port, boolean autoRead) throws ExecutionException, InterruptedException {
        final SettableFuture<ChannelFuture> hasConnected = SettableFuture.create();
        if (condition) {
            new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(childHandler)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.AUTO_READ, autoRead)
                    .bind(port).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        hasConnected.set(future);
                    } else {
                        hasConnected.setException(future.cause());
                    }
                }
            });
        } else {
            hasConnected.set(null);
        }
        return hasConnected.get();
    }

    protected void proxyStarted(final Integer port, boolean socksProxy) {
        System.setProperty("proxySet", "true");
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("java.net.useSystemProxies", "true");
        System.setProperty("http.proxyPort", port.toString());
        if (socksProxy) {
            previousProxySelector = java.net.ProxySelector.getDefault();
            System.setProperty("defaultProxySet", "true");
            System.setProperty("socksProxyHost", "127.0.0.1");
            System.setProperty("socksProxyPort", port.toString());
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
            proxyStopping();
            workerGroup.shutdownGracefully(2, 15, TimeUnit.SECONDS);
            bossGroup.shutdownGracefully(2, 15, TimeUnit.SECONDS);
            // wait for shutdown
            TimeUnit.SECONDS.sleep(3);
        } catch (Exception ie) {
            logger.trace("Exception while waiting for MockServer to stop", ie);
        }
    }

    public boolean isRunning() {
        if (hasStarted.isDone()) {
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                logger.trace("Exception while waiting for MockServer to confirm running status", e);
            }
            return !bossGroup.isShuttingDown() && !workerGroup.isShuttingDown();
        } else {
            return false;
        }
    }
}
