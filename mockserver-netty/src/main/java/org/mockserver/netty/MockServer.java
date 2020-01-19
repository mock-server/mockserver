package org.mockserver.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.action.ActionHandler;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.mockserver.mock.action.ActionHandler.REMOTE_SOCKET;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.netty.MockServerHandler.PROXYING;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;

/**
 * @author jamesdbloom
 */
public class MockServer extends LifeCycle {

    private InetSocketAddress remoteSocket;

    /**
     * Start the instance using the ports provided
     *
     * @param localPorts the local port(s) to use, use 0 or no vararg values to specify any free port
     */
    public MockServer(final Integer... localPorts) {
        this(proxyConfiguration(), localPorts);
    }

    /**
     * Start the instance using the ports provided configuring forwarded or proxied requests to go via an additional proxy
     *
     * @param proxyConfiguration the proxy configuration to send requests forwarded or proxied by MockServer via another proxy
     * @param localPorts         the local port(s) to use, use 0 or no vararg values to specify any free port
     */
    public MockServer(final ProxyConfiguration proxyConfiguration, final Integer... localPorts) {
        createServerBootstrap(proxyConfiguration, localPorts);

        // wait to start
        getLocalPort();
    }

    /**
     * Start the instance using the ports provided
     *
     * @param remotePort the port of the remote server to connect to
     * @param remoteHost the hostname of the remote server to connect to (if null defaults to "localhost")
     * @param localPorts the local port(s) to use
     */
    public MockServer(final Integer remotePort, @Nullable final String remoteHost, final Integer... localPorts) {
        this(proxyConfiguration(), remoteHost, remotePort, localPorts);
    }

    /**
     * Start the instance using the ports provided configuring forwarded or proxied requests to go via an additional proxy
     *
     * @param localPorts the local port(s) to use
     * @param remoteHost the hostname of the remote server to connect to (if null defaults to "localhost")
     * @param remotePort the port of the remote server to connect to
     */
    public MockServer(final ProxyConfiguration proxyConfiguration, @Nullable String remoteHost, final Integer remotePort, final Integer... localPorts) {
        if (remotePort == null) {
            throw new IllegalArgumentException("You must specify a remote hostname");
        }
        if (remoteHost == null) {
            remoteHost = "localhost";
        }

        remoteSocket = new InetSocketAddress(remoteHost, remotePort);
        if (proxyConfiguration != null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(SERVER_CONFIGURATION)
                    .setLogLevel(Level.INFO)
                    .setHttpRequest(request())
                    .setMessageFormat("using proxy configuration for forwarded requests:{}")
                    .setArguments(proxyConfiguration)
            );
        }
        createServerBootstrap(proxyConfiguration, localPorts);

        // wait to start
        getLocalPort();
    }

    private void createServerBootstrap(final ProxyConfiguration proxyConfiguration, final Integer... localPorts) {
        List<Integer> portBindings = singletonList(0);
        if (localPorts != null && localPorts.length > 0) {
            portBindings = Arrays.asList(localPorts);
        }

        serverServerBootstrap = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .option(ChannelOption.SO_BACKLOG, 1024)
            .channel(NioServerSocketChannel.class)
            .childOption(ChannelOption.AUTO_READ, true)
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
            .childHandler(new MockServerUnificationInitializer(MockServer.this, httpStateHandler, new ActionHandler(getEventLoopGroup(), httpStateHandler, proxyConfiguration)))
            .childAttr(REMOTE_SOCKET, remoteSocket)
            .childAttr(PROXYING, remoteSocket != null);

        try {
            bindServerPorts(portBindings);
        } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(SERVER_CONFIGURATION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception binding to port(s) " + portBindings)
                    .setThrowable(throwable)
            );
            stop();
            throw throwable;
        }
        startedServer(getLocalPorts());
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteSocket;
    }

}
