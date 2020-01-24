package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.cli.Main;
import org.mockserver.client.MockServerClient;
import org.mockserver.socket.PortFactory;
import org.mockserver.testing.integration.mock.AbstractExtendedSameJVMMockingIntegrationTest;

import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class SecureClientMockingIntegrationTest extends AbstractExtendedSameJVMMockingIntegrationTest {

    private static final int severHttpPort = PortFactory.findFreePort();

    @BeforeClass
    public static void startServer() {
        Main.main("-serverPort", "" + severHttpPort);

        mockServerClient = new MockServerClient("localhost", severHttpPort).withSecure(true);
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
