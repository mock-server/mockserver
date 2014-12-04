package org.mockserver.server;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.echo.EchoServer;
import org.mockserver.mockserver.MockServer;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class ClientServerNettyIntegrationTest extends AbstractClientServerSharedClassloadersAndTestClasspathIntegrationTest {

    private final static int TEST_SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static MockServer mockServer;
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() throws Exception {
        // start mock server
        mockServer = new MockServer(SERVER_HTTP_PORT);

        // start test server
        echoServer = new EchoServer(TEST_SERVER_HTTP_PORT);

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
    public int getMockServerPort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getMockServerSecurePort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getTestServerPort() {
        return TEST_SERVER_HTTP_PORT;
    }

}
