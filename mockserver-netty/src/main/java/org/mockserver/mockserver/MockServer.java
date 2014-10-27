package org.mockserver.mockserver;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.proxy.filters.LogFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 */
public class MockServer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mockserver
    private final MockServerMatcher mockServerMatcher = new MockServerMatcher();
    private final LogFilter logFilter = new LogFilter();
    private SettableFuture<String> hasStarted;
    // netty
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    /**
     * Start the instance using the ports provided
     *
     * @param port the http port to use
     * @param securePort the secure https port to use
     */
    public Thread start(final Integer port, final Integer securePort) {
        if (port == null && securePort == null) {
            throw new IllegalStateException("You must specify a port or a secure port");
        }

        hasStarted = SettableFuture.create();
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();

        Thread mockServerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Channel httpChannel = null;
                    logger.info("MockServer starting up"
                                    + (port != null ? " serverPort " + port : "")
                                    + (securePort != null ? " secureServerPort " + securePort : "")
                    );
                    if (port != null) {
                        httpChannel = new ServerBootstrap()
                                .group(bossGroup, workerGroup)
                                .channel(NioServerSocketChannel.class)
                                .childHandler(new MockServerInitializer(new MockServerHandler(mockServerMatcher, logFilter, MockServer.this, false)))
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
                                .childHandler(new MockServerInitializer(new MockServerHandler(mockServerMatcher, logFilter, MockServer.this, true)))
                                .option(ChannelOption.SO_BACKLOG, 1024)
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
        });
        mockServerThread.start();

        try {
            // wait for proxy to start all channels
            hasStarted.get();
        } catch (Exception e) {
            logger.debug("Exception while waiting for proxy to complete starting up", e);
        }

        return mockServerThread;
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
        if (hasStarted.isDone()) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return !bossGroup.isShuttingDown() && !workerGroup.isShuttingDown();
        } else {
            return false;
        }
    }
}
