package org.mockserver.netty.integration.proxy.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.base64.Base64;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.test.IsDebug;
import org.mockserver.uuid.UUIDService;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpHeaderNames.PROXY_AUTHORIZATION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.IsDebug.timeoutUnits;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class HttpProxyChainedIntegrationTest {

    private static ClientAndServer targetClientAndServer;

    private static final EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(HttpProxyChainedIntegrationTest.class.getSimpleName() + "-eventLoop"));

    @BeforeClass
    public static void startServer() {
        targetClientAndServer = startClientAndServer();
    }

    @AfterClass
    public static void stopEventLoopGroup() {
        clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(targetClientAndServer);

    }

    @Before
    public void reset() {
        targetClientAndServer.reset();
    }

    @Test
    public void shouldAuthenticateForwardHTTPConnect() throws Exception {
        String originalProxyAuthenticationUsername = ConfigurationProperties.proxyAuthenticationUsername();
        String originalProxyAuthenticationPassword = ConfigurationProperties.proxyAuthenticationPassword();
        String originalForwardProxyAuthenticationUsername = ConfigurationProperties.forwardProxyAuthenticationUsername();
        String originalForwardProxyAuthenticationPassword = ConfigurationProperties.forwardProxyAuthenticationPassword();
        InetSocketAddress originalForwardHttpsProxy = ConfigurationProperties.forwardHttpsProxy();
        ClientAndServer proxyClientAndServer = null;
        try {

            String username = UUIDService.getUUID();
            String password = UUIDService.getUUID();
            ConfigurationProperties.proxyAuthenticationUsername(username);
            ConfigurationProperties.proxyAuthenticationPassword(password);
            ConfigurationProperties.forwardHttpsProxy("localhost:" + targetClientAndServer.getPort());
            ConfigurationProperties.forwardProxyAuthenticationUsername(username);
            ConfigurationProperties.forwardProxyAuthenticationPassword(password);
            proxyClientAndServer = startClientAndServer();

            new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false)
                .sendRequest(
                    request()
                        .withPath("/target")
                        .withSecure(true)
                        .withHeader(HOST.toString(), "www.mock-server.com")
                        .withHeader(PROXY_AUTHORIZATION.toString(), "Basic " + Base64.encode(Unpooled.copiedBuffer(username + ':' + password, StandardCharsets.UTF_8), false).toString(StandardCharsets.US_ASCII)),
                    new InetSocketAddress(proxyClientAndServer.getLocalPort())
                )
                .get(10, timeoutUnits());

            // and - both proxy and target verify request received
            proxyClientAndServer.verify(request().withPath("/target"));
            targetClientAndServer.verify(request().withPath("/target"));

        } finally {
            if (proxyClientAndServer != null) {
                stopQuietly(proxyClientAndServer);
            }

            ConfigurationProperties.proxyAuthenticationUsername(originalProxyAuthenticationUsername);
            ConfigurationProperties.proxyAuthenticationPassword(originalProxyAuthenticationPassword);
            ConfigurationProperties.forwardProxyAuthenticationUsername(originalForwardProxyAuthenticationUsername);
            ConfigurationProperties.forwardProxyAuthenticationPassword(originalForwardProxyAuthenticationPassword);
            ConfigurationProperties.forwardHttpsProxy(originalForwardHttpsProxy != null ? originalForwardHttpsProxy.toString() : null);
        }
    }

    @Test
    public void shouldForwardHTTPConnect() throws Exception {
        InetSocketAddress originalForwardHttpsProxy = ConfigurationProperties.forwardHttpsProxy();
        ClientAndServer proxyClientAndServer = null;
        try {

            ConfigurationProperties.forwardHttpsProxy("localhost:" + targetClientAndServer.getLocalPort());
            proxyClientAndServer = startClientAndServer();

            new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false)
                .sendRequest(
                    request()
                        .withPath("/target")
                        .withSecure(true)
                        .withHeader(HOST.toString(), "www.mock-server.com"),
                    new InetSocketAddress(proxyClientAndServer.getLocalPort())
                )
                .get(10, timeoutUnits());

            // and - both proxy and target verify request received
            proxyClientAndServer.verify(request().withPath("/target"));
            targetClientAndServer.verify(request().withPath("/target"));

        } finally {
            if (proxyClientAndServer != null) {
                stopQuietly(proxyClientAndServer);
            }

            ConfigurationProperties.forwardHttpsProxy(originalForwardHttpsProxy != null ? originalForwardHttpsProxy.toString() : null);
        }
    }

    @Test
    public void shouldNotForwardHTTPConnectIfNotSecure() throws Exception {
        InetSocketAddress originalForwardHttpsProxy = ConfigurationProperties.forwardHttpsProxy();
        ClientAndServer proxyClientAndServer = null;
        try {

            ConfigurationProperties.forwardHttpsProxy("localhost:" + targetClientAndServer.getLocalPort());
            proxyClientAndServer = startClientAndServer();

            new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false)
                .sendRequest(
                    request()
                        .withPath("/target")
                        .withHeader(HOST.toString(), "www.mock-server.com"),
                    new InetSocketAddress(proxyClientAndServer.getLocalPort())
                )
                .get(10, timeoutUnits());

            // and - both proxy and target verify request received
            proxyClientAndServer.verify(request().withPath("/target"));
            targetClientAndServer.verify(request().withPath("/target"), exactly(0));

        } finally {
            if (proxyClientAndServer != null) {
                stopQuietly(proxyClientAndServer);
            }

            ConfigurationProperties.forwardHttpsProxy(originalForwardHttpsProxy != null ? originalForwardHttpsProxy.toString() : null);
        }
    }

}
