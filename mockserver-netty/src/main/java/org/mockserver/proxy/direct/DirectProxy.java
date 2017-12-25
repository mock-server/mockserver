package org.mockserver.proxy.direct;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.proxy.Proxy;

import java.net.InetSocketAddress;
import java.util.Arrays;

import static org.mockserver.mock.HttpStateHandler.STATE_HANDLER;
import static org.mockserver.mock.action.ActionHandler.REMOTE_SOCKET;

/**
 * @author jamesdbloom
 */
public class DirectProxy extends Proxy<DirectProxy> {

    private InetSocketAddress remoteSocket;

    /**
     * Start the instance using the ports provided
     *
     * @param localPorts the local port(s) to use
     * @param remoteHost the hostname of the remote server to connect to
     * @param remotePort the port of the remote server to connect to
     */
    public DirectProxy(final String remoteHost, final Integer remotePort, final Integer... localPorts) {
        if (remoteHost == null) {
            throw new IllegalArgumentException("You must specify a remote port");
        }
        if (remotePort == null) {
            throw new IllegalArgumentException("You must specify a remote hostname");
        }
        if (localPorts == null || localPorts.length == 0) {
            throw new IllegalArgumentException("You must specify at least one port");
        }

        remoteSocket = new InetSocketAddress(remoteHost, remotePort);
        serverBootstrap = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .option(ChannelOption.SO_BACKLOG, 1024)
            .channel(NioServerSocketChannel.class)
            .childOption(ChannelOption.AUTO_READ, true)
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
            .childHandler(new DirectProxyUnificationHandler())
            .childAttr(HTTP_PROXY, DirectProxy.this)
            .childAttr(REMOTE_SOCKET, remoteSocket)
//            .childAttr(PROXYING, true)
            .childAttr(STATE_HANDLER, new HttpStateHandler());

        bindToPorts(Arrays.asList(localPorts));

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                // Shut down all event loops to terminate all threads.
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();

                // Wait until all threads are terminated.
                try {
                    bossGroup.terminationFuture().sync();
                    workerGroup.terminationFuture().sync();
                } catch (InterruptedException e) {
                    // ignore interrupted exceptions
                }
            }
        }));
    }

    public int getLocalPort() {
        return getPort();
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteSocket;
    }

}
