package org.mockserver.integration.mockserver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.integration.server.AbstractBasicClientServerIntegrationTest;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class ClientAndServerIntegrationTest extends AbstractBasicClientServerIntegrationTest {

    private static int mockServerPort;
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {
        mockServerClient = startClientAndServer();
        mockServerPort = ((ClientAndServer) mockServerClient).getPort();

        echoServer = new EchoServer(false);
    }

    @AfterClass
    public static void stopServer() {
        mockServerClient.stop();

        echoServer.stop();
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
