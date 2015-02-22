package org.mockserver.integration;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.echo.EchoServer;
import org.mockserver.server.AbstractClientServerSharedClassloadersAndTestClasspathIntegrationTest;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.ExecutionException;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class ClientAndServerIntegrationTest extends AbstractClientServerSharedClassloadersAndTestClasspathIntegrationTest {

    private static int serverHttpPort;
    private final static int TEST_SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static EchoServer httpEchoServer;

    @BeforeClass
    public static void startServer() throws InterruptedException, ExecutionException {
        // start mock server and client
        ClientAndServer clientAndServer = startClientAndServer(0);

        serverHttpPort = clientAndServer.getPort();
        mockServerClient = clientAndServer;

        // start echo servers
        httpEchoServer = new EchoServer(TEST_SERVER_HTTP_PORT);
    }

    @AfterClass
    public static void stopServer() {
        // stop mock server and client
        if (mockServerClient instanceof ClientAndServer) {
            mockServerClient.stop();
        }

        // stop echo server
        httpEchoServer.stop();
    }

    @Override
    public int getMockServerPort() {
        return serverHttpPort;
    }

    @Override
    public int getMockServerSecurePort() {
        return serverHttpPort;
    }

    @Override
    public int getTestServerPort() {
        return TEST_SERVER_HTTP_PORT;
    }
}
