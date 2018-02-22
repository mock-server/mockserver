package org.mockserver.lifecycle;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.scheduler.Scheduler;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.mockserver.log.model.MessageLogEntry.LogMessageType.SERVER_CONFIGURATION;

/**
 * @author jamesdbloom
 */
public abstract class LifeCycle {

    static {
        new MockServerLogger();
    }

    protected final MockServerLogger mockServerLogger;
    protected EventLoopGroup bossGroup = new NioEventLoopGroup(ConfigurationProperties.nioEventLoopThreadCount());
    protected EventLoopGroup workerGroup = new NioEventLoopGroup(ConfigurationProperties.nioEventLoopThreadCount());
    protected HttpStateHandler httpStateHandler;
    protected ServerBootstrap serverServerBootstrap;
    protected ServerBootstrap dashboardServerBootstrap;
    private List<Future<Channel>> serverChannelFutures = new ArrayList<>();
    private List<Future<Channel>> dashboardChannelFutures = new ArrayList<>();
    private Scheduler scheduler = new Scheduler();

    protected LifeCycle() {
        this.httpStateHandler = new HttpStateHandler(scheduler);
        this.mockServerLogger = httpStateHandler.getMockServerLogger();
    }

    public void stop() {
        scheduler.shutdown();

        // Shut down all event loops to terminate all threads.
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();

        // Wait until all threads are terminated.
        bossGroup.terminationFuture().syncUninterruptibly();
        workerGroup.terminationFuture().syncUninterruptibly();
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

    public List<Integer> getDashboardPorts() {
        return getBoundPorts(dashboardChannelFutures);
    }

    public Integer getDashboardPort() {
        return getFirstBoundPort(dashboardChannelFutures);
    }

    private Integer getFirstBoundPort(List<Future<Channel>> channelFutures) {
        for (Future<Channel> channelOpened : channelFutures) {
            try {
                return ((InetSocketAddress) channelOpened.get(2, TimeUnit.SECONDS).localAddress()).getPort();
            } catch (Exception e) {
                mockServerLogger.trace("Exception while retrieving port from channel future, ignoring port for this channel", e);
            }
        }
        return -1;
    }

    private List<Integer> getBoundPorts(List<Future<Channel>> channelFutures) {
        List<Integer> ports = new ArrayList<>();
        for (Future<Channel> channelOpened : channelFutures) {
            try {
                ports.add(((InetSocketAddress) channelOpened.get(3, TimeUnit.SECONDS).localAddress()).getPort());
            } catch (Exception e) {
                mockServerLogger.trace("Exception while retrieving port from channel future, ignoring port for this channel", e);
            }
        }
        return ports;
    }

    public List<Integer> bindServerPorts(final List<Integer> requestedPortBindings) {
        return bindPorts(serverServerBootstrap, requestedPortBindings, serverChannelFutures);
    }

    public List<Integer> bindDashboardPorts(final List<Integer> requestedPortBindings) {
        return bindPorts(dashboardServerBootstrap, requestedPortBindings, dashboardChannelFutures);
    }

    private List<Integer> bindPorts(final ServerBootstrap serverBootstrap, List<Integer> requestedPortBindings, List<Future<Channel>> channelFutures) {
        List<Integer> actualPortBindings = new ArrayList<>();
        for (final Integer portToBind : requestedPortBindings) {
            try {
                final SettableFuture<Channel> channelOpened = SettableFuture.create();
                channelFutures.add(channelOpened);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            serverBootstrap
                                .bind(portToBind)
                                .addListener(new ChannelFutureListener() {
                                    @Override
                                    public void operationComplete(ChannelFuture future) {
                                        if (future.isSuccess()) {
                                            channelOpened.set(future.channel());
                                        } else {
                                            channelOpened.setException(future.cause());
                                        }
                                    }
                                })
                                .channel().closeFuture().syncUninterruptibly();

                        } catch (Exception e) {
                            throw new RuntimeException("Exception while binding MockServer to port " + portToBind, e);
                        }
                    }
                }, "MockServer thread for port: " + portToBind).start();

                actualPortBindings.add(((InetSocketAddress) channelOpened.get().localAddress()).getPort());
            } catch (Exception e) {
                throw new RuntimeException("Exception while binding MockServer to port " + portToBind, e.getCause());
            }
        }
        return actualPortBindings;
    }

    protected void startedServer(List<Integer> ports) {
        mockServerLogger.info(SERVER_CONFIGURATION, "MockServer started on port" + (ports.size() == 1 ? ": " + ports.get(0) : "s: " + ports));
    }

    protected void startedDashboard(List<Integer> ports) {
        mockServerLogger.info(SERVER_CONFIGURATION, "Dashboard bound to port" + (ports.size() == 1 ? ": " + ports.get(0) : "s: " + ports));
    }

}
