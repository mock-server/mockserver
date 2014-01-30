package org.mockserver.proxy;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.integration.proxy.ServerRunner;
import org.mockserver.netty.proxy.http.HttpProxy;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.ExecutionException;

/**
 * @author jamesdbloom
 */
//public class NettyHttpProxyIntegrationTest extends AbstractClientSecureProxyIntegrationTest {
public class NettyHttpProxyIntegrationTest extends AbstractClientProxyIntegrationTest {

    private final static int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTP_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTPS_PORT = PortFactory.findFreePort();
    private final static ServerRunner serverRunner = new ServerRunner();
    private static HttpProxy proxyRunner;
    private static ProxyClient proxyClient;

    @BeforeClass
    public static void startServer() throws Exception {
        serverRunner.startServer(SERVER_HTTP_PORT, SERVER_HTTPS_PORT);
    }

    @BeforeClass
    public static void startProxy() throws ExecutionException, InterruptedException {
        // start client
        proxyRunner = new HttpProxy(PROXY_HTTP_PORT, PROXY_HTTPS_PORT).run();

        // start client
        proxyClient = new ProxyClient("localhost", PROXY_HTTP_PORT);
    }

    @AfterClass
    public static void stopProxy() {
        proxyRunner.stop();
    }

    @AfterClass
    public static void stopServer() throws Exception {
        serverRunner.stopServer();
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
