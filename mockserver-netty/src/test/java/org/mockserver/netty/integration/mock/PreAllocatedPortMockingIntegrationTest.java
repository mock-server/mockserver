package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.socket.PortFactory;
import org.mockserver.testing.integration.mock.AbstractBasicMockingSameJVMIntegrationTest;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class PreAllocatedPortMockingIntegrationTest extends AbstractBasicMockingSameJVMIntegrationTest {

    private static final int severHttpPort = PortFactory.findFreePort();

    @BeforeClass
    public static void startServer() {
        mockServerClient = startClientAndServer(severHttpPort);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
    }

    @Override
    public int getServerPort() {
        return severHttpPort;
    }

}
