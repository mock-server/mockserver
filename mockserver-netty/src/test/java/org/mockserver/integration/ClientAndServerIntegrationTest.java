package org.mockserver.integration;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.server.NettyAbstractClientServerIntegrationTest;
import org.mockserver.socket.PortFactory;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class ClientAndServerIntegrationTest extends NettyAbstractClientServerIntegrationTest {

    private static final int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int TEST_SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {
        // start mock server and client
        mockServerClient = startClientAndServer(SERVER_HTTP_PORT);

        // start echo servers
        echoServer = new EchoServer(TEST_SERVER_HTTP_PORT, false);
    }

    @AfterClass
    public static void stopServer() {
        // stop mock server and client
        if (mockServerClient instanceof ClientAndServer) {
            mockServerClient.stop();
        }

        // stop echo server
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
        return TEST_SERVER_HTTP_PORT;
    }
}
