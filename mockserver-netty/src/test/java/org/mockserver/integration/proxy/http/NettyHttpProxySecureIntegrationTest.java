package org.mockserver.integration.proxy.http;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.proxy.AbstractClientSecureProxyIntegrationTest;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class NettyHttpProxySecureIntegrationTest extends AbstractClientSecureProxyIntegrationTest {

    private final static Integer PROXY_PORT = PortFactory.findFreePort();
    private static EchoServer echoServer;
    private static Proxy httpProxy;
    private static ProxyClient proxyClient;

    @BeforeClass
    public static void setupFixture() throws Exception {
        // start server
        echoServer = new EchoServer(true);

        // start proxy
        httpProxy = new ProxyBuilder()
            .withLocalPort(PROXY_PORT)
            .build();

        // start client
        proxyClient = new ProxyClient("localhost", PROXY_PORT);
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
        return PROXY_PORT;
    }

    @Override
    public ProxyClient getProxyClient() {
        return proxyClient;
    }

    @Override
    public int getServerSecurePort() {
        return echoServer.getPort();
    }
}
