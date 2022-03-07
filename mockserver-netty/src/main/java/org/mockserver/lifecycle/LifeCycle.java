package org.mockserver.lifecycle;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.listeners.MockServerMatcherNotifier;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.stop.Stoppable;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.mockserver.mock.HttpState.setPort;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public abstract class LifeCycle implements Stoppable {

    protected final MockServerLogger mockServerLogger;
    protected final EventLoopGroup bossGroup;
    protected final EventLoopGroup workerGroup;
    protected final HttpState httpState;
    private final Configuration configuration;
    protected ServerBootstrap serverServerBootstrap;
    private final List<Future<Channel>> serverChannelFutures = new ArrayList<>();
    private final CompletableFuture<String> stopFuture = new CompletableFuture<>();
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final Scheduler scheduler;

    protected LifeCycle(Configuration configuration) {
        this.configuration = configuration != null ? configuration : configuration();
        this.mockServerLogger = new MockServerLogger(MockServerEventLog.class);
        this.bossGroup = new NioEventLoopGroup(5, new Scheduler.SchedulerThreadFactory(this.getClass().getSimpleName() + "-bossEventLoop"));
        this.workerGroup = new NioEventLoopGroup(this.configuration.nioEventLoopThreadCount(), new Scheduler.SchedulerThreadFactory(this.getClass().getSimpleName() + "-workerEventLoop"));
        this.scheduler = new Scheduler(this.configuration, this.mockServerLogger);
        this.httpState = new HttpState(this.configuration, this.mockServerLogger, this.scheduler);
    }

    public CompletableFuture<String> stopAsync() {
        if (!stopFuture.isDone() && stopping.compareAndSet(false, true)) {
            final String message = "stopped for port" + (getLocalPorts().size() == 1 ? ": " + getLocalPorts().get(0) : "s: " + getLocalPorts());
            if (MockServerLogger.isEnabled(INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(SERVER_CONFIGURATION)
                        .setLogLevel(INFO)
                        .setMessageFormat(message)
                );
            }
            new Scheduler.SchedulerThreadFactory("Stop").newThread(() -> {
                List<ChannelFuture> collect = serverChannelFutures
                    .stream()
                    .flatMap(channelFuture -> {
                        try {
                            return Stream.of(channelFuture.get());
                        } catch (Throwable throwable) {
                            // ignore
                            throwable.printStackTrace();
                            return Stream.empty();
                        }
                    })
                    .map(ChannelOutboundInvoker::disconnect)
                    .collect(Collectors.toList());
                try {
                    for (ChannelFuture channelFuture : collect) {
                        channelFuture.get();
                    }
                } catch (Throwable throwable) {
                    // ignore
                    throwable.printStackTrace();
                }

                httpState.stop();
                scheduler.shutdown();

                // Shut down all event loops to terminate all threads.
                bossGroup.shutdownGracefully(5, 5, MILLISECONDS);
                workerGroup.shutdownGracefully(5, 5, MILLISECONDS);

                // Wait until all threads are terminated.
                bossGroup.terminationFuture().syncUninterruptibly();
                workerGroup.terminationFuture().syncUninterruptibly();

                stopFuture.complete(message);
            }).start();
        }
        return stopFuture;
    }

    public void stop() {
        try {
            stopAsync().get(10, SECONDS);
        } catch (Throwable throwable) {
            if (MockServerLogger.isEnabled(DEBUG)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(DEBUG)
                        .setMessageFormat("exception while stopping - " + throwable.getMessage())
                        .setArguments(throwable)
                );
            }
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
                if (MockServerLogger.isEnabled(WARN)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(WARN)
                            .setMessageFormat("exception while retrieving port from channel future, ignoring port for this channel - " + throwable.getMessage())
                            .setArguments(throwable)
                    );
                }
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
                if (MockServerLogger.isEnabled(DEBUG)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(DEBUG)
                            .setMessageFormat("exception while retrieving port from channel future, ignoring port for this channel")
                            .setArguments(e)
                    );
                }
            }
        }
        return ports;
    }

    public List<Integer> bindServerPorts(final List<Integer> requestedPortBindings) {
        return bindPorts(serverServerBootstrap, requestedPortBindings, serverChannelFutures);
    }

    private List<Integer> bindPorts(final ServerBootstrap serverBootstrap, List<Integer> requestedPortBindings, List<Future<Channel>> channelFutures) {
        List<Integer> actualPortBindings = new ArrayList<>();
        final String localBoundIP = configuration.localBoundIP();
        for (final Integer portToBind : requestedPortBindings) {
            try {
                final CompletableFuture<Channel> channelOpened = new CompletableFuture<>();
                channelFutures.add(channelOpened);
                new Scheduler.SchedulerThreadFactory("MockServer thread for port: " + portToBind, false).newThread(() -> {
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
                }).start();

                actualPortBindings.add(((InetSocketAddress) channelOpened.get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS).localAddress()).getPort());
            } catch (Exception e) {
                throw new RuntimeException("Exception while binding MockServer to port " + portToBind, e instanceof ExecutionException ? e.getCause() : e);
            }
        }
        return actualPortBindings;
    }

    protected void startedServer(List<Integer> ports) {
        final String message = "started on port" + (ports.size() == 1 ? ": " + ports.get(0) : "s: " + ports);
        setPort(ports);
        if (MockServerLogger.isEnabled(INFO)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(SERVER_CONFIGURATION)
                    .setLogLevel(INFO)
                    .setMessageFormat(message)
            );
        }
    }

    public LifeCycle registerListener(ExpectationsListener expectationsListener) {
        httpState.getRequestMatchers().registerListener((requestMatchers, cause) -> {
            if (cause == MockServerMatcherNotifier.Cause.API) {
                expectationsListener.updated(requestMatchers.retrieveActiveExpectations(null));
            }
        });
        return this;
    }

}
