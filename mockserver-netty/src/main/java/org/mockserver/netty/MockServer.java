package org.mockserver.netty;

import com.google.common.collect.ImmutableList;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.mockserver.authentication.ChainedAuthenticationHandler;
import org.mockserver.authentication.jwt.JWTAuthenticationHandler;
import org.mockserver.authentication.mtls.MTLSAuthenticationHandler;
import org.mockserver.configuration.Configuration;
import org.mockserver.lifecycle.ExpectationsListener;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.http.HttpActionHandler;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static org.mockserver.netty.HttpRequestHandler.PROXYING;
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
        this(null, proxyConfiguration(configuration()), localPorts);
    }

    /**
     * Start the instance using the ports provided
     *
     * @param localPorts the local port(s) to use, use 0 or no vararg values to specify any free port
     */
    public MockServer(final Configuration configuration, final Integer... localPorts) {
        this(configuration, proxyConfiguration(configuration), localPorts);
    }

    /**
     * Start the instance using the ports provided configuring forwarded or proxied requests to go via an additional proxy
     *
     * @param proxyConfiguration the proxy configuration to send requests forwarded or proxied by MockServer via another proxy
     * @param localPorts         the local port(s) to use, use 0 or no vararg values to specify any free port
     */
    public MockServer(final ProxyConfiguration proxyConfiguration, final Integer... localPorts) {
        this(null, ImmutableList.of(proxyConfiguration), localPorts);
    }

    /**
     * Start the instance using the ports provided configuring forwarded or proxied requests to go via an additional proxy
     *
     * @param proxyConfigurations the proxy configuration to send requests forwarded or proxied by MockServer via another proxy
     * @param localPorts          the local port(s) to use, use 0 or no vararg values to specify any free port
     */
    public MockServer(final Configuration configuration, final List<ProxyConfiguration> proxyConfigurations, final Integer... localPorts) {
        super(configuration);
        createServerBootstrap(configuration, proxyConfigurations, localPorts);

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
        this(null, proxyConfiguration(configuration()), remoteHost, remotePort, localPorts);
    }

    /**
     * Start the instance using the ports provided
     *
     * @param remotePort the port of the remote server to connect to
     * @param remoteHost the hostname of the remote server to connect to (if null defaults to "localhost")
     * @param localPorts the local port(s) to use
     */
    public MockServer(final Configuration configuration, final Integer remotePort, @Nullable final String remoteHost, final Integer... localPorts) {
        this(configuration, proxyConfiguration(configuration), remoteHost, remotePort, localPorts);
    }

    /**
     * Start the instance using the ports provided configuring forwarded or proxied requests to go via an additional proxy
     *
     * @param localPorts the local port(s) to use
     * @param remoteHost the hostname of the remote server to connect to (if null defaults to "localhost")
     * @param remotePort the port of the remote server to connect to
     */
    public MockServer(final Configuration configuration, final ProxyConfiguration proxyConfiguration, @Nullable String remoteHost, final Integer remotePort, final Integer... localPorts) {
        this(configuration, ImmutableList.of(proxyConfiguration), remoteHost, remotePort, localPorts);
    }

    /**
     * Start the instance using the ports provided configuring forwarded or proxied requests to go via an additional proxy
     *
     * @param localPorts the local port(s) to use
     * @param remoteHost the hostname of the remote server to connect to (if null defaults to "localhost")
     * @param remotePort the port of the remote server to connect to
     */
    public MockServer(final Configuration configuration, final List<ProxyConfiguration> proxyConfigurations, @Nullable String remoteHost, final Integer remotePort, final Integer... localPorts) {
        super(configuration);
        if (remotePort == null) {
            throw new IllegalArgumentException("You must specify a remote hostname");
        }
        if (isBlank(remoteHost)) {
            remoteHost = "localhost";
        }

        remoteSocket = new InetSocketAddress(remoteHost, remotePort);
        if (proxyConfigurations != null && MockServerLogger.isEnabled(Level.INFO)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(SERVER_CONFIGURATION)
                    .setLogLevel(Level.INFO)
                    .setMessageFormat("using proxy configuration for forwarded requests:{}")
                    .setArguments(proxyConfigurations)
            );
        }
        createServerBootstrap(configuration, proxyConfigurations, localPorts);

        // wait to start
        getLocalPort();
    }

    private void createServerBootstrap(Configuration configuration, final List<ProxyConfiguration> proxyConfigurations, final Integer... localPorts) {
        if (configuration == null) {
            configuration = configuration();
        }

        List<Integer> portBindings = singletonList(0);
        if (localPorts != null && localPorts.length > 0) {
            portBindings = Arrays.asList(localPorts);
        }

        NettySslContextFactory nettySslContextFactory = new NettySslContextFactory(configuration, mockServerLogger);
        if (configuration.controlPlaneTLSMutualAuthenticationRequired() && configuration.controlPlaneJWTAuthenticationRequired()) {
            httpState.setControlPlaneAuthenticationHandler(
                new ChainedAuthenticationHandler(
                    new MTLSAuthenticationHandler(mockServerLogger, nettySslContextFactory.trustCertificateChain(configuration.controlPlaneTLSMutualAuthenticationCAChain())),
                    new JWTAuthenticationHandler(mockServerLogger, configuration.controlPlaneJWTAuthenticationJWKSource())
                )
            );
        } else if (configuration.controlPlaneTLSMutualAuthenticationRequired()) {
            httpState.setControlPlaneAuthenticationHandler(new MTLSAuthenticationHandler(mockServerLogger, nettySslContextFactory.trustCertificateChain(configuration.controlPlaneTLSMutualAuthenticationCAChain())));
        } else if (configuration.controlPlaneJWTAuthenticationRequired()) {
            httpState.setControlPlaneAuthenticationHandler(new JWTAuthenticationHandler(mockServerLogger, configuration.controlPlaneJWTAuthenticationJWKSource()));
        }
        serverServerBootstrap = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .option(ChannelOption.SO_BACKLOG, 1024)
            .channel(NioServerSocketChannel.class)
            .childOption(ChannelOption.AUTO_READ, true)
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
            .childHandler(new MockServerUnificationInitializer(configuration, MockServer.this, httpState, new HttpActionHandler(configuration, getEventLoopGroup(), httpState, proxyConfigurations, nettySslContextFactory), nettySslContextFactory))
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

    public MockServer registerListener(ExpectationsListener expectationsListener) {
        super.registerListener(expectationsListener);
        return this;
    }

}
