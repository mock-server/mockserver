package org.mockserver.integration.mocking;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.mockserver.MockServer;

import static org.hamcrest.Matchers.containsString;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class ExtendedNettyMockingIntegrationTest extends AbstractExtendedNettyMockingIntegrationTest {

    private static int mockServerPort;
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {
        mockServerPort = new MockServer().getLocalPort();
        mockServerClient = new MockServerClient("localhost", mockServerPort, servletContext);

        echoServer = new EchoServer(false);
    }

    @AfterClass
    public static void stopServer() {
        if (mockServerClient != null) {
            mockServerClient.stop();
        }

        if (echoServer != null) {
            echoServer.stop();
        }
    }

    @Override
    public int getServerPort() {
        return mockServerPort;
    }

    @Override
    public int getEchoServerPort() {
        return echoServer.getPort();
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldThrowExceptionIfFailToBindToSocket() {
        // given
        System.out.println(NEW_LINE + NEW_LINE + "+++ IGNORE THE FOLLOWING java.net.BindException EXCEPTION +++" + NEW_LINE + NEW_LINE);
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage(containsString("Exception while binding MockServer to port "));

        // when
        startClientAndServer(mockServerPort);
    }
}
