package org.mockserver.proxy.socks;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.echo.EchoServer;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.socket.PortFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
@Ignore
public class NettySocksProxyIntegrationTest extends AbstractClientProxyIntegrationTest {

    private final static Logger logger = LoggerFactory.getLogger(NettySocksProxyIntegrationTest.class);

    private final static Integer SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static Integer SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_HTTP_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_DIRECT_PORT = PortFactory.findFreePort();
    private static EchoServer echoServer;
    private static Proxy httpProxy;
    private static ProxyClient proxyClient;
    private static ProxySelector previousProxySelector;

    private static ProxySelector createProxySelector(final String host, final int port) {
        return new ProxySelector() {
            @Override
            public List<java.net.Proxy> select(URI uri) {
                return Arrays.asList(
                        new java.net.Proxy(java.net.Proxy.Type.SOCKS, new InetSocketAddress(host, port)),
                        new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(host, port))
                );
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                logger.error("Connection could not be established to proxy at socket [" + sa + "]", ioe);
            }
        };
    }

    @BeforeClass
    public static void setupFixture() throws Exception {
        logger.debug("SERVER_HTTP_PORT = " + SERVER_HTTP_PORT);
        logger.debug("SERVER_HTTPS_PORT = " + SERVER_HTTPS_PORT);
        logger.debug("PROXY_HTTP_PORT = " + PROXY_HTTP_PORT);
        logger.debug("PROXY_DIRECT_PORT = " + PROXY_DIRECT_PORT);

        servletContext = "";

        // start server
        echoServer = new EchoServer(SERVER_HTTP_PORT);

        // start proxy
        httpProxy = new ProxyBuilder()
                .withLocalPort(PROXY_HTTP_PORT)
                .build();

        // start client
        proxyClient = new ProxyClient("localhost", PROXY_HTTP_PORT);

        previousProxySelector = ProxySelector.getDefault();
        ProxySelector.setDefault(createProxySelector("127.0.0.1", PROXY_HTTP_PORT));
        System.setProperty("socksProxySet", "true");
    }

    @AfterClass
    public static void shutdownFixture() {
        // stop server
        echoServer.stop();

        // stop proxy
        httpProxy.stop();

        ProxySelector.setDefault(previousProxySelector);
    }

    @Before
    public void resetProxy() {
        proxyClient.reset();
    }

    @Override
    public int getProxyPort() {
        return PROXY_HTTP_PORT;
    }

    @Override
    public ProxyClient getProxyClient() {
        return proxyClient;
    }

    @Override
    public int getServerPort() {
        return SERVER_HTTP_PORT;
    }
}
