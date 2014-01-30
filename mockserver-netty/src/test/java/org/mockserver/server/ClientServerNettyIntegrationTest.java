package org.mockserver.server;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.netty.mockserver.NettyMockServer;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class ClientServerNettyIntegrationTest extends AbstractClientServerIntegrationTest {

    private final static int serverPort = PortFactory.findFreePort();
    private final static int serverSecurePort = PortFactory.findFreePort();
    private static NettyMockServer mockServer;

    @BeforeClass
    public static void startServer() throws Exception {
        // start server
        mockServer = new NettyMockServer(serverPort, serverSecurePort).run();

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
