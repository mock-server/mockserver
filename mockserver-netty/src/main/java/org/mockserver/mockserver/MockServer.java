package org.mockserver.mockserver;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.mockserver.filters.LogFilter;
import org.mockserver.mock.MockServerMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 */
public class MockServer {

    public static final AttributeKey<LogFilter> LOG_FILTER = AttributeKey.valueOf("SERVER_LOG_FILTER");
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // ports
    private final Integer port;
    // mockserver
    private final MockServerMatcher mockServerMatcher = new MockServerMatcher();
    private final LogFilter logFilter = new LogFilter();
    private final SettableFuture<String> hasStarted;
    // netty
    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private Channel channel;

    /**
     * Start the instance using the ports provided
     *
     * @param port the http port to use
     */
    public MockServer(final Integer port) {
        if (port == null) {
            throw new IllegalStateException("You must specify a port");
        }

        hasStarted = SettableFuture.create();

        // capture the assigned port from the separate thread that starts the server
        final AtomicInteger assignedPort = new AtomicInteger(port);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (port == 0) {
                        logger.info("MockServer starting up on a JVM-assigned port");
                    } else {
                        logger.info("MockServer starting up on port: {}", port);
                    }

                    channel = new ServerBootstrap()
                            .group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .option(ChannelOption.SO_BACKLOG, 1024)
                            .childHandler(new MockServerInitializer(mockServerMatcher, MockServer.this, false))
                            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                            .childAttr(LOG_FILTER, logFilter)
                            .bind(port)
                            .sync()
                            .channel();

                    // cast the localAddress to an InetSocketAddress, as instructed by the localAddress() javadoc
                    InetSocketAddress localAddress = (InetSocketAddress) httpChannel.localAddress();

                    // get the actual assigned port from netty. if the passed-in port parameter was non-zero, this should be the same (assuming
                    // the port is free and the server was started successfully). if the incoming port was zero, the JVM will assign a free
                    // port automatically.
                    assignedPort.set(localAddress.getPort());

                    logger.info("MockServer successfully started on port: {}", assignedPort.get());

                    hasStarted.set("STARTED");

                    channel.closeFuture().sync();
                } catch (InterruptedException ie) {
                    logger.error("MockServer receive InterruptedException", ie);
                } finally {
                    bossGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
                    workerGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
                }
            }
        }).start();

        try {
            // wait for proxy to start all channels
            hasStarted.get();
        } catch (Exception e) {
            logger.debug("Exception while waiting for MockServer to complete starting up", e);
        }

        this.port = assignedPort.get();
    }

    public void stop() {
        try {
            bossGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
            workerGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
            channel.close();
            // wait for socket to be released
            TimeUnit.MILLISECONDS.sleep(500);
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
}
