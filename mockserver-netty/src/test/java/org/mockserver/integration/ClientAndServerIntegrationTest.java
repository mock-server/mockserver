package org.mockserver.integration;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.integration.testserver.TestServer;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.ExecutionException;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class ClientAndServerIntegrationTest extends AbstractClientServerIntegrationTest {

    private static final int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static final int SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static int TEST_SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int TEST_SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private static TestServer testServer = new TestServer();

    @BeforeClass
    public static void startServer() throws InterruptedException, ExecutionException {
        // start mock server and client
        mockServerClient = startClientAndServer(SERVER_HTTP_PORT, SERVER_HTTPS_PORT);

        // start test server
        testServer.startServer(TEST_SERVER_HTTP_PORT, TEST_SERVER_HTTPS_PORT);
    }

    @AfterClass
    public static void stopServer() {
        // stop mock server and client
        if (mockServerClient instanceof ClientAndServer) {
            mockServerClient.stop();
        }

        // stop test server
        if (testServer != null) {
            testServer.stop();
        }
    }

    @Override
    public int getMockServerPort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getMockServerSecurePort() {
        return SERVER_HTTPS_PORT;
    }

    @Override
    public int getTestServerPort() {
        return TEST_SERVER_HTTP_PORT;
    }

    @Override
    public int getTestServerSecurePort() {
        return TEST_SERVER_HTTPS_PORT;
    }
}
