package org.mockserver.proxy.http;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.integration.proxy.AbstractClientSecureProxyIntegrationTest;
import org.mockserver.integration.testserver.TestServer;
import org.mockserver.socket.PortFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class NettyHttpProxyIntegrationTest extends AbstractClientSecureProxyIntegrationTest {

    private final static Logger logger = LoggerFactory.getLogger(NettyHttpProxyIntegrationTest.class);

    private final static Integer SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static Integer SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_HTTP_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_HTTPS_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_SOCKS_PORT = null;
    private final static Integer PROXY_DIRECT_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_DIRECT_SECURE_PORT = PortFactory.findFreePort();
    private static TestServer testServer = new TestServer();
    private static HttpProxy httpProxy = new HttpProxy();
    private static ProxyClient proxyClient;

    @BeforeClass
    public static void setupFixture() throws Exception {
        logger.debug("SERVER_HTTP_PORT = " + SERVER_HTTP_PORT);
        logger.debug("SERVER_HTTPS_PORT = " + SERVER_HTTPS_PORT);
        logger.debug("PROXY_HTTP_PORT = " + PROXY_HTTP_PORT);
        logger.debug("PROXY_HTTPS_PORT = " + PROXY_HTTPS_PORT);
        logger.debug("PROXY_SOCKS_PORT = " + PROXY_SOCKS_PORT);
        logger.debug("PROXY_DIRECT_PORT = " + PROXY_DIRECT_PORT);
        logger.debug("PROXY_DIRECT_SECURE_PORT = " + PROXY_DIRECT_SECURE_PORT);

        // start server
        testServer.startServer(SERVER_HTTP_PORT, SERVER_HTTPS_PORT);

        // start proxy
        httpProxy.start(PROXY_HTTP_PORT, PROXY_HTTPS_PORT, PROXY_SOCKS_PORT, PROXY_DIRECT_PORT, PROXY_DIRECT_SECURE_PORT, "127.0.0.1", SERVER_HTTP_PORT);

        // start client
        proxyClient = new ProxyClient("localhost", PROXY_HTTP_PORT);
    }

    @AfterClass
    public static void shutdownFixture() {
        // stop server
        testServer.stop();

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
    public int getServerPort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getServerSecurePort() {
        return SERVER_HTTPS_PORT;
    }
}
