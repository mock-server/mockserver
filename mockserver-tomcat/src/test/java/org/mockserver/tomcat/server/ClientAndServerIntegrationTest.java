package org.mockserver.tomcat.server;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.tomcat.integration.ClientAndServer;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.ExecutionException;

import static org.mockserver.tomcat.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class ClientAndServerIntegrationTest extends AbstractClientServerIntegrationTest {

    private static final int serverPort = PortFactory.findFreePort();
    private static final int serverSecurePort = PortFactory.findFreePort();

    @BeforeClass
    public static void startServer() throws InterruptedException, ExecutionException {
        mockServerClient = startClientAndServer(serverPort, serverSecurePort);
    }

    @Override
    public int getPort() {
        return serverPort;
    }

    @Override
    public int getSecurePort() {
        return serverSecurePort;
    }

    @AfterClass
    public static void stopServer() {
        if (mockServerClient instanceof ClientAndServer) {
            ((ClientAndServer) mockServerClient).stop();
        }
    }
}
