package org.mockserver.tomcat.proxy;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.integration.proxy.AbstractClientSecureProxyIntegrationTest;
import org.mockserver.integration.proxy.ServerRunner;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.ExecutionException;

/**
 * @author jamesdbloom
 */
public class ProxyRunnerIntegrationTest extends AbstractClientProxyIntegrationTest {

    private final static int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTP_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTPS_PORT = PortFactory.findFreePort();
    private final static ServerRunner serverRunner = new ServerRunner();
    private static ProxyRunner proxyRunner;

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

    @BeforeClass
    public static void startServer() throws Exception {
        serverRunner.startServer(SERVER_HTTP_PORT, SERVER_HTTPS_PORT);
    }

    @BeforeClass
    public static void startProxy() throws ExecutionException, InterruptedException {
        proxyRunner = new ProxyRunner().start(PROXY_HTTP_PORT, PROXY_HTTPS_PORT);
    }

    @Before
    public void clearProxy() throws ExecutionException, InterruptedException {
        new ProxyClient("localhost", PROXY_HTTP_PORT).reset();
    }

    @AfterClass
    public static void stopProxy() {
        proxyRunner.stop();
    }

    @AfterClass
    public static void stopServer() throws Exception {
        serverRunner.stopServer();
    }
}
