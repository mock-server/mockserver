package org.mockserver.server;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.mockserver.MockServer;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class ClientServerNettyIntegrationTest extends AbstractClientServerIntegrationTest {

    private final static int serverPort = PortFactory.findFreePort();
    private final static int serverSecurePort = PortFactory.findFreePort();
    private static MockServer mockServer;

    @BeforeClass
    public static void startServer() throws Exception {
        // start server
        mockServer = new MockServer(serverPort, serverSecurePort);

        // start client
        mockServerClient = new MockServerClient("localhost", serverPort, servletContext);
    }

    @AfterClass
    public static void stopServer() throws Exception {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @Override
    public int getPort() {
        return serverPort;
    }

    @Override
    public int getSecurePort() {
        return serverSecurePort;
    }
}
