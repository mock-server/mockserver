package org.mockserver;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;

/**
 * @author jamesdbloom
 */
public class ClientServerMavenPluginIntegrationTest extends AbstractClientServerIntegrationTest {

    private final static int serverPort = 8080;
    private final static int serverSecurePort = 8082;

    @BeforeClass
    public static void startServer() throws Exception {
        // do nothing maven build should have started server

        // start client
        mockServerClient = new MockServerClient("localhost", serverPort, servletContext);
    }

    @Before
    public void clearServer() {
        mockServerClient.reset();
    }

    @Override
    public int getPort() {
        return serverPort;
    }

    @Override
    public int getSecurePort() {
        return serverSecurePort;
    }

    @AfterClass
    public static void stopServer() throws Exception {
        // do nothing maven build should stop server
    }

}
