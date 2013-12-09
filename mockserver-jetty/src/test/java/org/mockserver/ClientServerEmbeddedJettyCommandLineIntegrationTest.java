package org.mockserver;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.integration.AbstractClientServerIntegrationTest;
import org.mockserver.server.EmbeddedJettyRunner;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ClientServerEmbeddedJettyCommandLineIntegrationTest extends AbstractClientServerIntegrationTest {

    private final static int port = 8090;

    @BeforeClass
    public static void startServer() throws InterruptedException {
        EmbeddedJettyRunner.main("" + port);
        // wait for server to start up
        Thread.sleep(TimeUnit.SECONDS.toMillis(2));
    }

    @Before
    public void clearServer() throws InterruptedException {
        mockServerClient.reset();
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
