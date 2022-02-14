package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.netty.integration.ShadedJarRunner;
import org.mockserver.socket.PortFactory;
import org.mockserver.testing.integration.mock.AbstractBasicMockingIntegrationTest;

import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ExtendedShadedJarMockingIntegrationTest extends AbstractBasicMockingIntegrationTest {

    private static final int mockServerPort = PortFactory.findFreePort();

    @BeforeClass
    public static void startServerUsingShadedJar() throws Exception {
        mockServerClient = ShadedJarRunner.startServerUsingShadedJar(mockServerPort);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
    }

    @Override
    public int getServerPort() {
        return mockServerPort;
    }

}
