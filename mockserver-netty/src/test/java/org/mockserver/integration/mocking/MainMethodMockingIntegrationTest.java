package org.mockserver.integration.mocking;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.cli.Main;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.server.AbstractBasicMockingIntegrationTest;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class MainMethodMockingIntegrationTest extends AbstractBasicMockingIntegrationTest {

    private static int severHttpPort = PortFactory.findFreePort();

    @BeforeClass
    public static void startServer() {
        Main.main("-serverPort", "" + severHttpPort);

        mockServerClient = new MockServerClient("localhost", severHttpPort);
    }

    @AfterClass
    public static void stopServer() {
        if (mockServerClient != null) {
            mockServerClient.stop();
        }
    }

    @Override
    public int getServerPort() {
        return severHttpPort;
    }

    @Override
    public int getEchoServerPort() {
        return insecureEchoServer.getPort();
    }
}
