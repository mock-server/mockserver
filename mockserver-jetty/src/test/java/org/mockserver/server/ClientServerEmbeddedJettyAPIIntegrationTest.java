package org.mockserver.server;

import org.junit.After;
import org.junit.Before;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;

/**
 * @author jamesdbloom
 */
public class ClientServerEmbeddedJettyAPIIntegrationTest extends AbstractClientServerIntegrationTest {

    private MockServerRunner mockServerRunner;
    private final int serverPort = 8090;

    @Before
    public void startServer() {
        mockServerRunner = new MockServerRunner();
        mockServerRunner.start(serverPort);
    }

    @Override
    public int getPort() {
        return serverPort;
    }

    @After
    public void stopServer() {
        mockServerRunner.stop();
    }
}
