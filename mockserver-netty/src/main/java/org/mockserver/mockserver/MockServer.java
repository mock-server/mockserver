package org.mockserver.mockserver;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.mockserver.filters.LogFilter;
import org.mockserver.mock.MockServerMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 */
public class MockServer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // ports
    private final Integer port;
    private final Integer securePort;
    // mockserver
    private final LogFilter logFilter = new LogFilter();
    private final MockServerMatcher mockServerMatcher = new MockServerMatcher();
    private final SettableFuture<String> hasStarted;
    // netty
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    /**
     * Start the instance using the ports provided
     *
     * @param port the http port to use
     * @param securePort the secure https port to use
     */
    public MockServer(final Integer port, final Integer securePort) {
        if (port == null && securePort == null) {
            throw new IllegalStateException("You must specify a port or a secure port");
        }
        this.port = port;
        this.securePort = securePort;

        hasStarted = SettableFuture.create();
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    logger.info("MockServer starting up"
                                    + (port != null ? " serverPort " + port : "")
                                    + (securePort != null ? " secureServerPort " + securePort : "")
                    );

                    Channel httpChannel = null;
                    if (port != null) {
                        httpChannel = new ServerBootstrap()
                                .option(ChannelOption.SO_BACKLOG, 1024)
                                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                                .group(bossGroup, workerGroup)
                                .channel(NioServerSocketChannel.class)
                                .childHandler(new MockServerInitializer(mockServerMatcher, logFilter, MockServer.this, false))
                                .bind(port)
                                .sync()
                                .channel();
                    }

                    Channel httpsChannel = null;
                    if (securePort != null) {
                        httpsChannel = new ServerBootstrap()
                                .option(ChannelOption.SO_BACKLOG, 1024)
                                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                                .group(bossGroup, workerGroup)
                                .channel(NioServerSocketChannel.class)
                                .childHandler(new MockServerInitializer(mockServerMatcher, logFilter, MockServer.this, true))
                                .bind(securePort)
                                .sync()
                                .channel();
                    }

                    hasStarted.set("STARTED");

                    if (httpChannel != null) {
                        httpChannel.closeFuture().sync();
                    }
                    if (httpsChannel != null) {
                        httpsChannel.closeFuture().sync();
                    }
                } catch (InterruptedException ie) {
                    logger.error("MockServer receive InterruptedException", ie);
                } finally {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        }).start();

        try {
            // wait for proxy to start all channels
            hasStarted.get();
        } catch (Exception e) {
            logger.debug("Exception while waiting for proxy to complete starting up", e);
        }
    }

    public void stop() {
        try {
            bossGroup.shutdownGracefully(1, 3, TimeUnit.MILLISECONDS);
            workerGroup.shutdownGracefully(1, 3, TimeUnit.MILLISECONDS);
            // wait for shutdown
            TimeUnit.SECONDS.sleep(1);
        } catch (Exception ie) {
            logger.trace("Exception while stopping MockServer", ie);
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

    public Integer getSecurePort() {
        return securePort;
    }
}
