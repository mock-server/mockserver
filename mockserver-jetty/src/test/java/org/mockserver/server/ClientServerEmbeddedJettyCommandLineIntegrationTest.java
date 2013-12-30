package org.mockserver.server;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.cli.Main;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ClientServerEmbeddedJettyCommandLineIntegrationTest extends AbstractClientServerIntegrationTest {

    private final static int serverPort = 9090;

    @BeforeClass
    public static void startServer() throws InterruptedException {
        Main.reset();
        Main.main("-serverPort", "" + serverPort);
        // wait for server to start up
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    }

    @AfterClass
    public static void stopServer() {
        new MockServerRunner().stop("127.0.0.1", serverPort + 1, "STOP_KEY", 500);
    }

    @Before
    public void clearServer() throws InterruptedException {
        mockServerClient.reset();
    }

    @Override
    public int getPort() {
        return serverPort;
    }

}
