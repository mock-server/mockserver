package org.mockserver;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.integration.testserver.TestServer;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class ClientServerMavenPluginIntegrationTest extends AbstractClientServerIntegrationTest {

    private final static int TEST_SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int TEST_SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTP_PORT = 8087;
    private final static int SERVER_HTTPS_PORT = 8088;
    private static TestServer testServer = new TestServer();

    @BeforeClass
    public static void createClient() throws Exception {
        // do nothing maven build should have started server

        // start test server
        testServer.startServer(TEST_SERVER_HTTP_PORT, TEST_SERVER_HTTPS_PORT);

        // start client
        mockServerClient = new MockServerClient("localhost", SERVER_HTTP_PORT, servletContext);
    }

    @Before
    public void clearServer() {
        mockServerClient.reset();
    }

    @AfterClass
    public static void stopServer() throws Exception {
        // do nothing maven build should stop server

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
