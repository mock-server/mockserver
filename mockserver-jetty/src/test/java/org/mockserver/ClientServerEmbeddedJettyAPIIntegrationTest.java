package org.mockserver;

import org.junit.After;
import org.junit.Before;
import org.mockserver.integration.AbstractClientServerIntegrationTest;
import org.mockserver.server.EmbeddedJettyRunner;

/**
 * @author jamesdbloom
 */
public class ClientServerEmbeddedJettyAPIIntegrationTest extends AbstractClientServerIntegrationTest {

    private EmbeddedJettyRunner embeddedJettyRunner;
    private final int port = 8090;

    @Before
    public void startServer() {
        embeddedJettyRunner = new EmbeddedJettyRunner();
        embeddedJettyRunner.start(port);
    }

    @Override
    public int getPort() {
        return port;
    }

    @After
    public void stopServer() {
        embeddedJettyRunner.stop();
    }
}
