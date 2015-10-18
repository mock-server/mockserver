package org.mockserver.integration.mockserver;

import org.junit.Test;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.MockServer;
import org.mockserver.socket.PortFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class MockServerStopTest {

    private final static int serverPort = PortFactory.findFreePort();

    @Test
    public void canStartAndStopMultipleTimes() {
        // start server
        MockServer mockServer = new MockServer(serverPort);

        // start client
        MockServerClient mockServerClient = new MockServerClient("localhost", serverPort);

        for (int i = 0; i < 2; i++) {
            // when
            mockServerClient.stop();

            // then
            assertFalse(mockServer.isRunning());
            mockServer = new MockServer(serverPort);
            assertTrue(mockServer.isRunning());
        }

        assertTrue(mockServer.isRunning());
        mockServer.stop();
        assertFalse(mockServer.isRunning());
    }
}
