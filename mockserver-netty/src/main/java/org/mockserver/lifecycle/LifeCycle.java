package org.mockserver.lifecycle;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.stop.Stoppable;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.configuration.ConfigurationProperties.maxFutureTimeout;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.mockserver.model.HttpRequest.request;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public abstract class LifeCycle implements Stoppable {

    protected final MockServerLogger mockServerLogger;
    protected EventLoopGroup bossGroup = new NioEventLoopGroup(5, new Scheduler.SchedulerThreadFactory(this.getClass().getSimpleName() + "-bossEventLoop"));
    protected EventLoopGroup workerGroup = new NioEventLoopGroup(ConfigurationProperties.nioEventLoopThreadCount(), new Scheduler.SchedulerThreadFactory(this.getClass().getSimpleName() + "-workerEventLoop"));
    protected HttpStateHandler httpStateHandler;
    protected ServerBootstrap serverServerBootstrap;
    private List<Future<Channel>> serverChannelFutures = new ArrayList<>();
    private Scheduler scheduler;

    protected LifeCycle() {
        this.mockServerLogger = new MockServerLogger(MockServerEventLog.class);
        this.scheduler = new Scheduler(this.mockServerLogger);
        this.httpStateHandler = new HttpStateHandler(this.mockServerLogger, this.scheduler);
    }

    public Future<String> stopAsync() {
        CompletableFuture<String> stopFuture = new CompletableFuture<>();
        new Scheduler.SchedulerThreadFactory("Stop").newThread(() -> {
            scheduler.shutdown();
            httpStateHandler.stop();

            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully(5, 5, MILLISECONDS);
            workerGroup.shutdownGracefully(5, 5, MILLISECONDS);

            // Wait until all threads are terminated.
            bossGroup.terminationFuture().syncUninterruptibly();
            workerGroup.terminationFuture().syncUninterruptibly();

            try {
                GlobalEventExecutor.INSTANCE.awaitInactivity(2, SECONDS);
            } catch (InterruptedException ignore) {
                // ignore interruption
            }
            stopFuture.complete("done");
        }).start();
        return stopFuture;
    }

    public void stop() {
        try {
            stopAsync().get(10, SECONDS);
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.TRACE)
                    .setLogLevel(DEBUG)
                    .setMessageFormat("exception while stopping - " + throwable.getMessage())
                    .setArguments(throwable)
            );
        }
    }

    @Override
    public void close() {
        stop();
    }

    protected EventLoopGroup getEventLoopGroup() {
        return workerGroup;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public boolean isRunning() {
        return !bossGroup.isShuttingDown() || !workerGroup.isShuttingDown();
    }

    public List<Integer> getLocalPorts() {
        return getBoundPorts(serverChannelFutures);
    }

    /**
     * @deprecated use getLocalPort instead of getPort
     */
    @Deprecated
    public Integer getPort() {
        return getLocalPort();
    }

    public int getLocalPort() {
        return getFirstBoundPort(serverChannelFutures);
    }

    private Integer getFirstBoundPort(List<Future<Channel>> channelFutures) {
        for (Future<Channel> channelOpened : channelFutures) {
            try {
                return ((InetSocketAddress) channelOpened.get(15, SECONDS).localAddress()).getPort();
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.WARN)
                        .setLogLevel(WARN)
                        .setMessageFormat("exception while retrieving port from channel future, ignoring port for this channel - " + throwable.getMessage())
                        .setArguments(throwable)
                );
            }
        }
        return -1;
    }

    private List<Integer> getBoundPorts(List<Future<Channel>> channelFutures) {
        List<Integer> ports = new ArrayList<>();
        for (Future<Channel> channelOpened : channelFutures) {
            try {
                ports.add(((InetSocketAddress) channelOpened.get(3, SECONDS).localAddress()).getPort());
            } catch (Exception e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.TRACE)
                        .setLogLevel(TRACE)
                        .setMessageFormat("exception while retrieving port from channel future, ignoring port for this channel")
                        .setArguments(e)
                );
            }
        }
        return ports;
    }

    public List<Integer> bindServerPorts(final List<Integer> requestedPortBindings) {
        return bindPorts(serverServerBootstrap, requestedPortBindings, serverChannelFutures);
    }

    private List<Integer> bindPorts(final ServerBootstrap serverBootstrap, List<Integer> requestedPortBindings, List<Future<Channel>> channelFutures) {
        List<Integer> actualPortBindings = new ArrayList<>();
        final String localBoundIP = ConfigurationProperties.localBoundIP();
        for (final Integer portToBind : requestedPortBindings) {
            try {
                final CompletableFuture<Channel> channelOpened = new CompletableFuture<>();
                channelFutures.add(channelOpened);
                new Thread(() -> {
                    try {
                        InetSocketAddress inetSocketAddress;
                        if (isBlank(localBoundIP)) {
                            inetSocketAddress = new InetSocketAddress(portToBind);
                        } else {
                            inetSocketAddress = new InetSocketAddress(localBoundIP, portToBind);
                        }
                        serverBootstrap
                            .bind(inetSocketAddress)
                            .addListener((ChannelFutureListener) future -> {
                                if (future.isSuccess()) {
                                    channelOpened.complete(future.channel());
                                } else {
                                    channelOpened.completeExceptionally(future.cause());
                                }
                            })
                            .channel().closeFuture().syncUninterruptibly();

                    } catch (Exception e) {
                        channelOpened.completeExceptionally(new RuntimeException("Exception while binding MockServer to port " + portToBind, e));
                    }
                }, "MockServer thread for port: " + portToBind).start();

                actualPortBindings.add(((InetSocketAddress) channelOpened.get(maxFutureTimeout(), MILLISECONDS).localAddress()).getPort());
            } catch (Exception e) {
                throw new RuntimeException("Exception while binding MockServer to port " + portToBind, e.getCause());
            }
        }
        return actualPortBindings;
    }

    protected void startedServer(List<Integer> ports) {
        final String message = "started on port" + (ports.size() == 1 ? ": " + ports.get(0) : "s: " + ports);
        mockServerLogger.logEvent(
            new LogEntry()
                .setType(SERVER_CONFIGURATION)
                .setLogLevel(INFO)
                .setHttpRequest(request())
                .setMessageFormat(message)
        );
    }

}
