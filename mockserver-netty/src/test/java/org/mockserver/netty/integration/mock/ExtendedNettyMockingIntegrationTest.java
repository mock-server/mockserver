package org.mockserver.netty.integration.mock;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;

import static org.hamcrest.Matchers.containsString;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class ExtendedNettyMockingIntegrationTest extends AbstractExtendedNettyMockingIntegrationTest {

    private static int mockServerPort;

    @BeforeClass
    public static void startServer() {
        mockServerPort = new MockServer().getLocalPort();
        mockServerClient = new MockServerClient("localhost", mockServerPort, servletContext);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
    }

    @Override
    public int getServerPort() {
        return mockServerPort;
    }

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldThrowExceptionIfFailToBindToSocket() {
        // given
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage(containsString("Exception while binding MockServer to port "));

        // when
        startClientAndServer(mockServerPort);
    }
}
