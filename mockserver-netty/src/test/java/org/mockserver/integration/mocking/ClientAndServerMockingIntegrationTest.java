package org.mockserver.integration.mocking;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.integration.server.AbstractBasicMockingIntegrationTest;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class ClientAndServerMockingIntegrationTest extends AbstractBasicMockingIntegrationTest {

    private static int mockServerPort;
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {
        mockServerClient = startClientAndServer();
        mockServerPort = ((ClientAndServer) mockServerClient).getLocalPort();

        echoServer = new EchoServer(false);
    }

    @AfterClass
    public static void stopServer() {
        if (mockServerClient != null) {
            mockServerClient.stop();
        }

        if (echoServer != null) {
            echoServer.stop();
        }
    }

    @Override
    public int getMockServerPort() {
        return mockServerPort;
    }

    @Override
    public int getMockServerSecurePort() {
        return mockServerPort;
    }

    @Override
    public int getTestServerPort() {
        return echoServer.getPort();
    }
}
