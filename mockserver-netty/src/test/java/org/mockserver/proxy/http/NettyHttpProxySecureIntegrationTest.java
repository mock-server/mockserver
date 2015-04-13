package org.mockserver.proxy.http;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.proxy.AbstractClientSecureProxyIntegrationTest;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.socket.PortFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class NettyHttpProxySecureIntegrationTest extends AbstractClientSecureProxyIntegrationTest {

    private final static Logger logger = LoggerFactory.getLogger(NettyHttpProxySecureIntegrationTest.class);

    private final static Integer SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_HTTPS_PORT = PortFactory.findFreePort();
    private static EchoServer echoServer;
    private static Proxy httpProxy;
    private static ProxyClient proxyClient;

    @BeforeClass
    public static void setupFixture() throws Exception {
        logger.debug("SERVER_HTTPS_PORT = " + SERVER_HTTPS_PORT);
        logger.debug("PROXY_HTTPS_PORT = " + PROXY_HTTPS_PORT);

        // start server
        echoServer = new EchoServer(SERVER_HTTPS_PORT, true);

        // start proxy
        httpProxy = new ProxyBuilder()
                .withLocalPort(PROXY_HTTPS_PORT)
                .build();

        // start client
        proxyClient = new ProxyClient("localhost", PROXY_HTTPS_PORT);
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
        return PROXY_HTTPS_PORT;
    }

    @Override
    public ProxyClient getProxyClient() {
        return proxyClient;
    }

    @Override
    public int getServerSecurePort() {
        return SERVER_HTTPS_PORT;
    }
}
