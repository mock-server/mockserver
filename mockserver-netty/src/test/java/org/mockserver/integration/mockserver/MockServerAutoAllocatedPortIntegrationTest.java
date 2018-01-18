package org.mockserver.integration.mockserver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.integration.server.AbstractBasicClientServerIntegrationTest;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.ExecutionException;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class MockServerAutoAllocatedPortIntegrationTest extends AbstractBasicClientServerIntegrationTest {

    private static int severHttpPort;
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {
        // start mock server and client
        mockServerClient = startClientAndServer(0);
        severHttpPort = ((ClientAndServer) mockServerClient).getPort();

        // start echo servers
        echoServer = new EchoServer(false);
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
