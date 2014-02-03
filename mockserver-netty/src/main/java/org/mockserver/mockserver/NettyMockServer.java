package org.mockserver.mockserver;

import ch.qos.logback.classic.Level;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mockserver.mock.MockServer;
import org.mockserver.proxy.filters.LogFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 */
public class NettyMockServer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mockserver
    private final MockServer mockServer = new MockServer();
    private final LogFilter logFilter = new LogFilter();
    private boolean hasBeenStarted = false;
    // netty
    private EventLoopGroup bossGroup = new NioEventLoopGroup();
    private EventLoopGroup workerGroup = new NioEventLoopGroup();

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
    public NettyMockServer start(final Integer port, final Integer securePort) {
        if (port == null && securePort == null) throw new IllegalStateException("You must specify a port or a secure port");
        hasBeenStarted = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Channel httpChannel = null;
                    if (port != null) {
                        httpChannel = new ServerBootstrap()
                                .group(bossGroup, workerGroup)
                                .channel(NioServerSocketChannel.class)
                                .childHandler(new MockServerInitializer(mockServer, logFilter, NettyMockServer.this, false))
                                .option(ChannelOption.SO_BACKLOG, 1024)
                                .bind(port)
                                .sync()
                                .channel();
                    }
                    Channel httpsChannel = null;
                    if (securePort != null) {
                        httpsChannel = new ServerBootstrap()
                                .group(bossGroup, workerGroup)
                                .channel(NioServerSocketChannel.class)
                                .childHandler(new MockServerInitializer(mockServer, logFilter, NettyMockServer.this, true))
                                .option(ChannelOption.SO_BACKLOG, 1024)
                                .bind(securePort)
                                .sync()
                                .channel();
                    }
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
        return this;
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
