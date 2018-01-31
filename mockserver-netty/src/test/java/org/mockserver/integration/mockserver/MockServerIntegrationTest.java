package org.mockserver.integration.mockserver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.mockserver.MockServer;

import static org.hamcrest.Matchers.containsString;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class MockServerIntegrationTest extends AbstractMockServerNettyIntegrationTest {

    private static int mockServerPort;
    private static MockServer mockServer;
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {
        mockServer = new MockServer();
        mockServerPort = mockServer.getPort();

        echoServer = new EchoServer( false);

        mockServerClient = new MockServerClient("localhost", mockServerPort, servletContext);
    }

    @AfterClass
    public static void stopServer() {
        mockServer.stop();

        echoServer.stop();
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

    @Override
    public int getMockServerPort() {
        return mockServerPort;
    }

    @Override
    public int getMockServerSecurePort() {
        return mockServerPort;
    }

    @Override
    public int getTestServerPort() {
        return echoServer.getPort();
    }
}
