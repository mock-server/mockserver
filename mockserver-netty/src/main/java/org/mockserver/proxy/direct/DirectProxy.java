package org.mockserver.proxy.direct;

import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mockserver.filters.RequestLogFilter;
import org.mockserver.filters.RequestResponseLogFilter;
import org.mockserver.proxy.Proxy;
import org.mockserver.stop.StopEventQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.Future;

/**
 * This class should not be constructed directly instead use HttpProxyBuilder to build and configure this class
 *
 * @author jamesdbloom
 * @see org.mockserver.proxy.ProxyBuilder
 */
public class DirectProxy implements Proxy {

    private static final Logger logger = LoggerFactory.getLogger(DirectProxy.class);
    // proxy
    private final RequestLogFilter requestLogFilter = new RequestLogFilter();
    private final RequestResponseLogFilter requestResponseLogFilter = new RequestResponseLogFilter();
    private final SettableFuture<String> hasStarted;
    private final SettableFuture<String> stopping = SettableFuture.<String>create();
    // netty
    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private StopEventQueue stopEventQueue = new StopEventQueue();
    private Channel channel;
    // remote socket
    private InetSocketAddress remoteSocket;

    /**
     * Start the instance using the ports provided
     *
     * @param localPort  the local port to expose
     * @param remoteHost the hostname of the remote server to connect to
     * @param remotePort the port of the remote server to connect to
     */
    public DirectProxy(final Integer localPort, final String remoteHost, final Integer remotePort) {
        if (localPort == null) {
            throw new IllegalArgumentException("You must specify a local port");
        }
        if (remoteHost == null) {
            throw new IllegalArgumentException("You must specify a remote port");
        }
        if (remotePort == null) {
            throw new IllegalArgumentException("You must specify a remote hostname");
        }

        hasStarted = SettableFuture.create();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    remoteSocket = new InetSocketAddress(remoteHost, remotePort);
                    channel = new ServerBootstrap()
                            .group(bossGroup, workerGroup)
                            .option(ChannelOption.SO_BACKLOG, 1024)
                            .channel(NioServerSocketChannel.class)
                            .childOption(ChannelOption.AUTO_READ, true)
                            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                            .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
                            .childHandler(new DirectProxyUnificationHandler())
                            .childAttr(HTTP_PROXY, DirectProxy.this)
                            .childAttr(REMOTE_SOCKET, remoteSocket)
                            .childAttr(REQUEST_LOG_FILTER, requestLogFilter)
                            .childAttr(REQUEST_RESPONSE_LOG_FILTER, requestResponseLogFilter)
                            .bind(localPort)
                            .syncUninterruptibly()
                            .channel();

                    logger.info("MockServer proxy started on port: {} connected to remote server: {}", ((InetSocketAddress) channel.localAddress()).getPort(), remoteHost + ":" + remotePort);

                    hasStarted.set("STARTED");

                    channel.closeFuture().syncUninterruptibly();
                } finally {
                    bossGroup.shutdownGracefully().syncUninterruptibly();
                    workerGroup.shutdownGracefully().syncUninterruptibly();
                }
            }
        }, "MockServer DirectProxy Thread").start();

        try {
            hasStarted.get();
        } catch (Exception e) {
            logger.warn("Exception while waiting for MockServer proxy to complete starting up", e);
        }
    }

    public Future<?> stop() {
        return stopEventQueue.stop(this, stopping, bossGroup, workerGroup, Collections.singletonList(channel));
    }

    public DirectProxy withStopEventQueue(StopEventQueue stopEventQueue) {
        this.stopEventQueue = stopEventQueue;
        this.stopEventQueue.register(this);
        return this;
    }

    public boolean isRunning() {
        return !bossGroup.isShuttingDown() || !workerGroup.isShuttingDown() || !stopping.isDone();
    }

    public Integer getLocalPort() {
        return ((InetSocketAddress) channel.localAddress()).getPort();
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteSocket;
    }

}
