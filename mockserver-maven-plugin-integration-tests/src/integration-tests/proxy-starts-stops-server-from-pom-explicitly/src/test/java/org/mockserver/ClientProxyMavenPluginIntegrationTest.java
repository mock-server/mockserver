package org.mockserver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.integration.testserver.TestServer;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class ClientProxyMavenPluginIntegrationTest extends AbstractClientProxyIntegrationTest {

    private final static int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTP_PORT = 9095;
    private final static int PROXY_HTTPS_PORT = 9096;
    private static TestServer testServer = new TestServer();

    @BeforeClass
    public static void startServer() throws Exception {
        testServer.startServer(SERVER_HTTP_PORT, SERVER_HTTPS_PORT);
    }

    @AfterClass
    public static void stopServer() throws Exception {
        testServer.stop();
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
