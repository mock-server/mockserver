package org.mockserver.mockserver;

import com.google.common.base.Joiner;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.exception.ExceptionContext;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.filters.LogFilter;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 */
public class MockServer {

    public static final AttributeKey<LogFilter> LOG_FILTER = AttributeKey.valueOf("SERVER_LOG_FILTER");
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // mockserver
    private final MockServerMatcher mockServerMatcher;
    private final LogFilter logFilter = new LogFilter();
    private final SettableFuture<Integer> hasStarted;
    // netty
    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private Channel channel;

    public MockServer(final Integer port) {
        this(port, null);
    }

    /**
     * Start the instance using the ports provided
     *
     * @param port the http port to use
     */
    public MockServer(final Integer port, final String databaseFile) {
        if (port == null) {
            throw new IllegalArgumentException("You must specify a port");
        }

        hasStarted = SettableFuture.create();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                logger.debug("Shutdown hook called.");
                saveExpectations(mockServerMatcher.getExpectations(), databaseFile);
            }
        });

        //
        List<Expectation> expectations = loadExpectations(databaseFile);

        this.mockServerMatcher = new MockServerMatcher(expectations);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    channel = new ServerBootstrap()
                            .group(bossGroup, workerGroup)
                            .option(ChannelOption.SO_BACKLOG, 1024)
                            .channel(NioServerSocketChannel.class)
                            .childOption(ChannelOption.AUTO_READ, true)
                            .childHandler(new MockServerInitializer(mockServerMatcher, MockServer.this))
                            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                            .childAttr(LOG_FILTER, logFilter)
                            .bind(port)
                            .addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    if (future.isSuccess()) {
                                        hasStarted.set(((InetSocketAddress) future.channel().localAddress()).getPort());
                                    } else {
                                        hasStarted.setException(future.cause());
                                    }
                                }
                            })
                            .channel();

                    logger.info("MockServer started on port: {}", hasStarted.get());


                    channel.closeFuture().syncUninterruptibly();

                } catch (Exception e) {
                    throw new RuntimeException("Exception while starting MockServer", e.getCause());
                } finally {
                    bossGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);
                    workerGroup.shutdownGracefully(0, 1, TimeUnit.MILLISECONDS);

                }
            }
        }).start();

        try {
            hasStarted.get();
        } catch (Exception e) {
            logger.error("Exception while waiting for MockServer to complete starting up", e);
        }
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

    public void saveExpectations(List<Expectation> expectations, String path) {

        if (path == null) {
            return;
        }
        // serialize expectations
        ExpectationSerializer serializer = new ExpectationSerializer();
        String serialized = serializer.serialize(expectations.toArray(new Expectation[expectations.size()]));

        // Write expectations to a file. Do not use Java 7 features, like try-with-resources statement, to stay
        // compatible with Java 6.
        try {
            logger.info("Try to save expectations to file {}", path);
            com.google.common.io.Files.write(serialized, new File(path), Charset.forName("UTF8"));
            logger.info("{} expectations are saved to file {}.", expectations.size(), path);
        } catch (IOException e) {
            logger.error("Unexpected IO error occurred, do not save expectations.", e);
        }
    }

    public List<Expectation> loadExpectations(String path) {

        if (path == null) {
            logger.info("Do not use database file.");
            return new ArrayList<Expectation>();
        }

        File f = new File(path);

        //com.google.common.io.Files.read
        ExpectationSerializer serializer = new ExpectationSerializer();
        String deserialized = null;
        try {
            if (!f.exists()) {
                logger.info("Create database file {}", path);
                f.createNewFile();
            }
            List<String> lines = com.google.common.io.Files.readLines(new File(path), Charset.forName("UTF8"));
            deserialized = Joiner.on("\n").join(lines);
        } catch (IOException e) {
            logger.error("Unable to load expectations from file.", e);
            return new ArrayList<Expectation>();
        }

        Expectation[] expectations = serializer.deserializeArray(deserialized);
        logger.info("{} epectation(s) successfully loaded from file {}.", expectations.length, path);
        return new ArrayList<Expectation>(Arrays.asList(expectations));
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
        try {
            return hasStarted.get();
        } catch (Exception e) {
            throw new RuntimeException("Exception while starting MockServer", e);
        }
    }
}
