package org.mockserver.server;

import org.junit.After;
import org.junit.Before;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.ExecutionException;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class ClientAndServerIntegrationTest extends AbstractClientServerIntegrationTest {

    private final int serverPort = PortFactory.findFreePort();
    private final int serverSecurePort = PortFactory.findFreePort();

    @Before
    public void startServer() throws InterruptedException, ExecutionException {
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

    @After
    public void stopServer() {
        ((ClientAndServer) mockServerClient).stop();
    }
}
