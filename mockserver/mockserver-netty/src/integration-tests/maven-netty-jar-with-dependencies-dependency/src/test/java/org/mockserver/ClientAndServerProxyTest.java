package org.mockserver;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.testing.integration.proxy.AbstractProxyIntegrationTest;

/**
 * @author jamesdbloom
 */
public class ClientAndServerProxyTest extends AbstractProxyIntegrationTest {

    private static EchoServer echoServer;
    private static ClientAndServer mockServerClient;

    @BeforeClass
    public static void startServer() {
        echoServer = new EchoServer(false);
        mockServerClient = ClientAndServer.startClientAndServer();
    }

    @AfterClass
    public static void stopServer() {
        if (echoServer != null) {
            echoServer.stop();
        }
        if (mockServerClient != null) {
            mockServerClient.stop();
        }
    }

    @Before
    public void resetProxy() {
        mockServerClient.reset();
    }

    @Override
    public int getProxyPort() {
        return mockServerClient.getLocalPort();
    }

    @Override
    public MockServerClient getMockServerClient() {
        return mockServerClient;
    }

    @Override
    public int getServerPort() {
        return echoServer.getPort();
    }

    @Override
    public EchoServer getEchoServer() {
        return echoServer;
    }

}
