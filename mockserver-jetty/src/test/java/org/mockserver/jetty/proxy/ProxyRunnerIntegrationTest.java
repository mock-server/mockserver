package org.mockserver.jetty.proxy;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.integration.proxy.AbstractClientSecureProxyIntegrationTest;
import org.mockserver.integration.proxy.ServerRunner;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.ExecutionException;

/**
 * @author jamesdbloom
 */
public class ProxyRunnerIntegrationTest extends AbstractClientSecureProxyIntegrationTest {

    private final static int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTP_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTPS_PORT = PortFactory.findFreePort();
    private final static ServerRunner serverRunner = new ServerRunner();
    private ProxyRunner proxyRunner;

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

    @Before
    public void startProxy() throws ExecutionException, InterruptedException {
        proxyRunner = new ProxyRunner().start(PROXY_HTTP_PORT, PROXY_HTTPS_PORT);
    }

    @After
    public void stopProxy() {
        proxyRunner.stop();
    }

    @AfterClass
    public static void stopServer() throws Exception {
        serverRunner.stopServer();
    }
}
