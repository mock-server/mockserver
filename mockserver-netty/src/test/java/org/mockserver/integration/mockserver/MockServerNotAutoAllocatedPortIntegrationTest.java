package org.mockserver.integration.mockserver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.server.AbstractBasicClientServerIntegrationTest;
import org.mockserver.socket.PortFactory;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class MockServerNotAutoAllocatedPortIntegrationTest extends AbstractBasicClientServerIntegrationTest {

    private static int severHttpPort = PortFactory.findFreePort();
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {
        mockServerClient = startClientAndServer(severHttpPort);

        echoServer = new EchoServer(false);
    }

    @AfterClass
    public static void stopServer() {
        mockServerClient.stop();

        echoServer.stop();
    }

    @Override
    public int getMockServerPort() {
        return severHttpPort;
    }

    @Override
    public int getMockServerSecurePort() {
        return severHttpPort;
    }

    @Override
    public int getTestServerPort() {
        return echoServer.getPort();
    }
}
