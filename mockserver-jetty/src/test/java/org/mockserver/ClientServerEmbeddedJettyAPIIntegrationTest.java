package org.mockserver;

import org.mockserver.integration.AbstractClientServerIntegrationTest;
import org.mockserver.server.EmbeddedJettyRunner;

/**
 * @author jamesdbloom
 */
public class ClientServerEmbeddedJettyAPIIntegrationTest extends AbstractClientServerIntegrationTest {

    private EmbeddedJettyRunner embeddedJettyRunner;
    private final int port = 8090;

    @Override
    public void startServer() {
        embeddedJettyRunner = new EmbeddedJettyRunner();
        embeddedJettyRunner.start(port);
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void stopServer() {
        embeddedJettyRunner.stop();
    }
}
