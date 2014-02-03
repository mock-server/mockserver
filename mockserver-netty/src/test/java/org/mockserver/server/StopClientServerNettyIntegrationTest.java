package org.mockserver.server;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.NettyMockServer;
import org.mockserver.socket.PortFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class StopClientServerNettyIntegrationTest {

    private final static int serverPort = PortFactory.findFreePort();
    private final static int serverSecurePort = PortFactory.findFreePort();

    @Test
    public void clientCanClearServerExpectations() {
        NettyMockServer mockServer = new NettyMockServer();

        assertFalse(mockServer.isRunning());

        // start server
        mockServer.start(serverPort, serverSecurePort);

        // start client
        MockServerClient mockServerClient = new MockServerClient("localhost", serverPort);

        for (int i = 0; i < 5; i++) {
            System.out.println("MockServer stop start test = " + i);
            // when
            mockServerClient.stop();

            // then
            assertFalse(mockServer.isRunning());
            mockServer = new NettyMockServer().start(serverPort, serverSecurePort);
            assertTrue(mockServer.isRunning());
        }

        assertTrue(mockServer.isRunning());
        mockServer.stop();
        assertFalse(mockServer.isRunning());
    }
}
