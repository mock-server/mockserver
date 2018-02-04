package org.mockserver.integration.mocking;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.cli.Main;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.server.AbstractBasicMockingIntegrationTest;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class MainMethodMockingIntegrationTest extends AbstractBasicMockingIntegrationTest {

    private static int severHttpPort = PortFactory.findFreePort();
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {
        Main.main("-serverPort", "" + severHttpPort);

        mockServerClient = new MockServerClient("localhost", severHttpPort);

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
        return severHttpPort;
    }

    @Override
    public int getMockServerSecurePort() {
        return severHttpPort;
    }

    @Override
    public int getTestServerPort() {
        return echoServer.getPort();
    }
}
