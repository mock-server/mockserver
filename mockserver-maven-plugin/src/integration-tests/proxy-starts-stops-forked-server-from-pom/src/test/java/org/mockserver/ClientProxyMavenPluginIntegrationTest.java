package org.mockserver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.integration.proxy.ServerRunner;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ClientProxyMavenPluginIntegrationTest extends AbstractClientProxyIntegrationTest {

    private final static int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTP_PORT = 9090;
    private final static int PROXY_HTTPS_PORT = 9092;
    private final static ServerRunner serverRunner = new ServerRunner();

    @BeforeClass
    public static void startServer() throws Exception {
        serverRunner.startServer(SERVER_HTTP_PORT, SERVER_HTTPS_PORT);
        // wait for server to start up
        Thread.sleep(TimeUnit.MILLISECONDS.toMillis(500));
    }

    @AfterClass
    public static void stopServer() throws Exception {
        serverRunner.stopServer();
        // wait for server to shutdown
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
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
