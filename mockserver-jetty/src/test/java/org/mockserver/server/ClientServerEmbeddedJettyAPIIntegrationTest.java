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

    private final int serverPort = 8090;
    private MockServerRunner mockServerRunner;

    @Before
    public void startServer() throws InterruptedException, ExecutionException {
        mockServerRunner = new MockServerRunner();
        try {
            mockServerRunner.start(serverPort, null).get(2, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // do nothing
        }
    }

    @Override
    public int getPort() {
        return serverPort;
    }

    @After
    public void stopServer() {
        mockServerRunner.stop();
    }
}
