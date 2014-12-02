package org.mockserver.proxy.direct;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.proxy.Proxy;
import org.mockserver.socket.SSLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * This class should not be constructed directly instead use HttpProxyBuilder to build and configure this class
 *
 * @see org.mockserver.proxy.ProxyBuilder
 *
 * @author jamesdbloom
 */
public class DirectProxy implements Proxy {

    private static final Logger logger = LoggerFactory.getLogger(DirectProxy.class);
    // proxy
    private final SettableFuture<String> hasStarted = SettableFuture.create();
    // netty
    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    // ports
    private final Integer localPort;
    private final String remoteHost;
    private final Integer remotePort;

    public DirectProxy(final Integer localPort, final String remoteHost, final Integer remotePort) {

        if (localPort == null) {
            throw new IllegalArgumentException("Port must not be null");
        }
        if (remoteHost == null) {
            throw new IllegalArgumentException("Port must not be null");
        }
        if (remotePort == null) {
            throw new IllegalArgumentException("Port must not be null");
        }

        this.localPort = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    new ServerBootstrap()
                            .group(bossGroup, workerGroup)
                            .option(ChannelOption.SO_BACKLOG, 1024)
                            .channel(NioServerSocketChannel.class)
                            .childOption(ChannelOption.AUTO_READ, true)
                            .childHandler(new DirectProxyUnificationHandler())
                            .childAttr(HTTP_PROXY, DirectProxy.this)
                            .childAttr(REMOTE_SOCKET, new InetSocketAddress(remoteHost, remotePort))
                            .bind(localPort)
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

    public void stop() {
        try {
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

    public Integer getLocalPort() {
        return localPort;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public Integer getRemotePort() {
        return remotePort;
    }
}
