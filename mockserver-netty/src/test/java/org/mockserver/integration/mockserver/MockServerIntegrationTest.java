package org.mockserver.integration.mockserver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.mockserver.MockServer;
import org.mockserver.socket.PortFactory;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class MockServerIntegrationTest extends AbstractRestartableMockServerNettyIntegrationTest {

    private final static int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static MockServer mockServer;
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {
        // start mock server
        mockServer = new MockServer(SERVER_HTTP_PORT);

        // start test server
        echoServer = new EchoServer( false);

        // start client
        mockServerClient = new MockServerClient("localhost", SERVER_HTTP_PORT, servletContext);
    }

    @AfterClass
    public static void stopServer() throws Exception {
        // stop mock server
        mockServer.stop();

        // stop test server
        echoServer.stop();
    }

    @Override
    public void startServerAgain() {
        startClientAndServer(SERVER_HTTP_PORT);
    }

    @Override
    public int getMockServerPort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getMockServerSecurePort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getTestServerPort() {
        return echoServer.getPort();
    }
}
