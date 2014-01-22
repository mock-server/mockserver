package org.mockserver.jetty.server;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.jetty.cli.Main;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.configuration.SystemProperties;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class MockServerCommandLineIntegrationTest extends AbstractClientServerIntegrationTest {

    private final static int serverPort = PortFactory.findFreePort();
    private final static int serverSecurePort = PortFactory.findFreePort();

    @BeforeClass
    public static void startServer() throws InterruptedException {
        Main.reset();
        Main.main("-serverPort", "" + serverPort, "-serverSecurePort", "" + serverSecurePort);
        // wait for server to start up
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
        mockServerClient = new MockServerClient("localhost", serverPort, servletContext);
    }

    @AfterClass
    public static void stopServer() {
        new MockServerRunner().stop("127.0.0.1", SystemProperties.serverStopPort(serverPort, serverSecurePort), 5);
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
