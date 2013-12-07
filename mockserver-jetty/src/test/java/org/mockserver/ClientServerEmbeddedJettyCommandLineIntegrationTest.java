package org.mockserver;

import org.junit.*;
import org.mockserver.integration.AbstractClientServerIntegrationTest;
import org.mockserver.model.HttpRequest;
import org.mockserver.server.EmbeddedJettyRunner;

/**
 * @author jamesdbloom
 */
public class ClientServerEmbeddedJettyCommandLineIntegrationTest extends AbstractClientServerIntegrationTest {

    private final static int port = 8090;

    @BeforeClass
    public static void startServer() {
        EmbeddedJettyRunner.main("" + port);
    }

    @Before
    public void clearServer() {
        mockServerClient.clear(new HttpRequest());
    }

    @Override
    public int getPort() {
        return port;
    }

    @AfterClass
    public static void stopServer() {
        EmbeddedJettyRunner.stopRemote("127.0.0.1", port + 1, "STOP_KEY", 500);
    }

}
