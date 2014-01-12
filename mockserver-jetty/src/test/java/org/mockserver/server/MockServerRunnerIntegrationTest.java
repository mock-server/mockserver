package org.mockserver.server;

import org.junit.After;
import org.junit.Before;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author jamesdbloom
 */
public class MockServerRunnerIntegrationTest extends AbstractClientServerIntegrationTest {

    private final int serverPort = PortFactory.findFreePort();
    private final int serverSecurePort = PortFactory.findFreePort();
    private MockServerRunner mockServerRunner;

    @Before
    public void startServer() throws InterruptedException, ExecutionException {
        mockServerRunner = new MockServerRunner().start(serverPort, serverSecurePort);
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
