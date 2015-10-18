package org.mockserver.integration.mockserver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.PortFactory;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * @author jamesdbloom
 */
public class MockServerMultiplePortIntegrationTest extends AbstractMockServerNettyIntegrationTest {

    private final static int TEST_SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static Integer[] severHttpPort;
    private static EchoServer echoServer;
    private final Random random = new Random();

    @BeforeClass
    public static void startServer() throws InterruptedException, ExecutionException {
        // start mock server and client
        mockServerClient = startClientAndServer(0, PortFactory.findFreePort(), 0, PortFactory.findFreePort());
        List<Integer> boundPorts = ((ClientAndServer) mockServerClient).getPorts();
        severHttpPort = boundPorts.toArray(new Integer[boundPorts.size()]);

        // start echo servers
        echoServer = new EchoServer(TEST_SERVER_HTTP_PORT, false);
    }

    @AfterClass
    public static void stopServer() {
        // stop mock server and client
        if (mockServerClient instanceof ClientAndServer) {
            mockServerClient.stop();
        }

        // stop echo server
        echoServer.stop();
    }

    @Override
    public void startServerAgain() {
        startClientAndServer(severHttpPort);
    }

    @Override
    public int getMockServerPort() {
        return severHttpPort[random.nextInt(severHttpPort.length)];
    }

    @Override
    public int getMockServerSecurePort() {
        return severHttpPort[random.nextInt(severHttpPort.length)];
    }

    @Override
    public int getTestServerPort() {
        return TEST_SERVER_HTTP_PORT;
    }
}
