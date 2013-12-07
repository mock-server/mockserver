package org.mockserver;

import org.junit.Ignore;
import org.mockserver.integration.AbstractClientServerIntegrationTest;
import org.mockserver.server.EmbeddedJettyRunner;

import java.io.File;
import java.io.IOException;

/**
 * @author jamesdbloom
 */
@Ignore
public class ClientServerEmbeddedJettyCommandLineIntegrationTest extends AbstractClientServerIntegrationTest {

    private final int port = 8090;

    @Override
    public void startServer() {
        EmbeddedJettyRunner.main("" + port);
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void stopServer() {
        mockServerClient.stopServer();
    }
}
