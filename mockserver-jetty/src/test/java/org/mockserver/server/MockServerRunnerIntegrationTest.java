package org.mockserver.server;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.server.AbstractClientServerIntegrationTest;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.ExecutionException;

/**
 * @author jamesdbloom
 */
public class MockServerRunnerIntegrationTest extends AbstractClientServerIntegrationTest {

    private static final int serverPort = PortFactory.findFreePort();
    private static final int serverSecurePort = PortFactory.findFreePort();
    private static MockServerRunner mockServerRunner;

    @BeforeClass
    public static void startServer() throws InterruptedException, ExecutionException {
        mockServerRunner = new MockServerRunner().start(serverPort, serverSecurePort);
        mockServerClient = new MockServerClient("localhost", serverPort, servletContext);
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
        if (mockServerRunner != null) {
            mockServerRunner.stop();
        }
    }
}
