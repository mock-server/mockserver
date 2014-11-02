package org.mockserver.server;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.testserver.TestServer;
import org.mockserver.mockserver.MockServer;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class ClientServerNettyIntegrationTest extends AbstractClientServerSharedClassloadersAndTestClasspathIntegrationTest {

    private final static int TEST_SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int TEST_SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private static MockServer mockServer = new MockServer();
    private static TestServer testServer = new TestServer();

    @BeforeClass
    public static void startServer() throws Exception {
        // start mock server
        mockServer.start(SERVER_HTTP_PORT, SERVER_HTTPS_PORT);

        // start test server
        testServer.startServer(TEST_SERVER_HTTP_PORT, TEST_SERVER_HTTPS_PORT);

        // start client
        mockServerClient = new MockServerClient("localhost", SERVER_HTTP_PORT, servletContext);
    }

    @AfterClass
    public static void stopServer() throws Exception {
        // stop mock server
        if (mockServer != null) {
            mockServer.stop();
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
