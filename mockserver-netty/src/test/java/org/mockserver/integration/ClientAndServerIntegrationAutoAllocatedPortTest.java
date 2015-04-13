package org.mockserver.integration;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.server.AbstractClientServerSharedClassloadersAndTestClasspathIntegrationTest;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.ExecutionException;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class ClientAndServerIntegrationAutoAllocatedPortTest extends AbstractClientServerSharedClassloadersAndTestClasspathIntegrationTest {

    private static int severHttpPort;
    private final static int TEST_SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() throws InterruptedException, ExecutionException {
        // start mock server and client
        mockServerClient = startClientAndServer(0);
        severHttpPort = ((ClientAndServer)mockServerClient).getPort();

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
    public int getMockServerPort() {
        return severHttpPort;
    }

    @Override
    public int getMockServerSecurePort() {
        return severHttpPort;
    }

    @Override
    public int getTestServerPort() {
        return TEST_SERVER_HTTP_PORT;
    }
}
