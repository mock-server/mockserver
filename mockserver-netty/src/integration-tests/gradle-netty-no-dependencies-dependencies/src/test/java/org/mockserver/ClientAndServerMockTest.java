package org.mockserver;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.testing.integration.mock.AbstractBasicMockingIntegrationTest;

/**
 * @author jamesdbloom
 */
public class ClientAndServerMockTest extends AbstractBasicMockingIntegrationTest {

    @BeforeClass
    public static void createClient() {
        mockServerClient = ClientAndServer.startClientAndServer();
    }

    @AfterClass
    public static void stopServer() {
        if (mockServerClient != null) {
            mockServerClient.stop();
        }
    }

    @Before
    public void clearServer() {
        mockServerClient.reset();
    }

    @Override
    public int getServerPort() {
        return mockServerClient.getPort();
    }

}
