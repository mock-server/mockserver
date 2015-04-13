package org.mockserver;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.echo.unification.PortUnificationEchoServer;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class ClientProxyMavenPluginTest extends AbstractClientProxyIntegrationTest {

    private final static int ECHO_SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTP_PORT = 9096;
    private static PortUnificationEchoServer echoServer;
    private static ProxyClient proxyClient;

    @BeforeClass
    public static void startServer() throws Exception {
        echoServer = new PortUnificationEchoServer(ECHO_SERVER_HTTP_PORT);
        proxyClient = new ProxyClient("127.0.0.1", PROXY_HTTP_PORT);
    }

    @AfterClass
    public static void stopServer() throws Exception {
        echoServer.stop();
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
        return ECHO_SERVER_HTTP_PORT;
    }

}
