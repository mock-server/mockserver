package org.mockserver.server;

import org.junit.After;
import org.junit.Before;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author jamesdbloom
 */
public class ClientServerEmbeddedJettyAPIIntegrationTest extends AbstractClientServerIntegrationTest {

    private final int serverPort = 9090;
    private final int serverSecurePort = 9091;
    private MockServerRunner mockServerRunner;

    @Before
    public void startServer() throws InterruptedException, ExecutionException {
        mockServerRunner = new MockServerRunner();
        try {
            mockServerRunner.start(serverPort, serverSecurePort).get(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // do nothing
        }
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
        mockServerRunner.stop();
    }
}
