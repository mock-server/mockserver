package org.mockserver;

import org.junit.After;
import org.junit.Before;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;

/**
 * @author jamesdbloom
 */
public class ClientServerMavenPluginIntegrationTest extends AbstractClientServerIntegrationTest {

    private final static int port = 8080;
    private final static int serverSecurePort = 8082;

    @Before
    public void startServer() throws Exception {
        // do nothing maven build should have started server
    }

    @Before
    public void clearServer() {
        mockServerClient.reset();
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public int getSecurePort() {
        return serverSecurePort;
    }

    @After
    public void stopServer() throws Exception {
        // do nothing maven build should stop server
    }

}
