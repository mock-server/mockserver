package org.mockserver.integration.proxy.http;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class NettyHttpProxyIntegrationTest extends AbstractClientProxyIntegrationTest {

    private final static Integer PROXY_HTTP_PORT = PortFactory.findFreePort();
    private static EchoServer echoServer;
    private static Proxy httpProxy;
    private static ProxyClient proxyClient;

    @BeforeClass
    public static void setupFixture() {
        servletContext = "";

        // start server
        echoServer = new EchoServer(false);

        // start proxy
        httpProxy = new ProxyBuilder()
            .withLocalPort(PROXY_HTTP_PORT)
            .build();

        // start client
        proxyClient = new ProxyClient("localhost", PROXY_HTTP_PORT);
    }

    @AfterClass
    public static void shutdownFixture() {
        // stop server
        echoServer.stop();

        // stop proxy
        httpProxy.stop();
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
        return echoServer.getPort();
    }
}
