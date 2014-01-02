package org.mockserver.server;

import org.eclipse.jetty.server.ShutdownMonitor;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.cli.Main;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;

import java.util.concurrent.TimeUnit;

import static org.mockserver.configuration.SystemProperties.stopPort;

/**
 * @author jamesdbloom
 */
public class ClientServerEmbeddedJettyCommandLineIntegrationTest extends AbstractClientServerIntegrationTest {

    private final static int serverPort = 9090;
    private final static int serverSecurePort = 9091;

    @BeforeClass
    public static void startServer() throws InterruptedException {
        Main.reset();
        Main.main("-serverPort", "" + serverPort, "-serverSecurePort", "" + serverSecurePort);
        // wait for server to start up
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    }

    @AfterClass
    public static void stopServer() {
        new MockServerRunner().stop("127.0.0.1", stopPort(serverPort, serverSecurePort), 5);
    }

    @Before
    public void clearServer() throws InterruptedException {
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

}
