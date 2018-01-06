package org.mockserver.integration.mockserver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.integration.server.AbstractBasicClientServerIntegrationTest;
import org.mockserver.model.HttpResponse;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.socket.PortFactory;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpResponse.notFoundResponse;

/**
 * @author jamesdbloom
 */
public class ClientAndProxyMockingIntegrationTest extends AbstractBasicClientServerIntegrationTest {

    private static final int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {
        // start proxy and client
        new ProxyBuilder().withLocalPort(SERVER_HTTP_PORT).build();
        mockServerClient = new MockServerClient("localhost", SERVER_HTTP_PORT);

        // start echo servers
        echoServer = new EchoServer(false);
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
    public int getMockServerPort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getMockServerSecurePort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getTestServerPort() {
        return echoServer.getPort();
    }
}
