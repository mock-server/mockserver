package org.mockserver.integration.mockserver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.server.AbstractBasicClientServerIntegrationTest;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.socket.PortFactory;

/**
 * @author jamesdbloom
 */
public class ClientAndProxyMockingIntegrationTest extends AbstractBasicClientServerIntegrationTest {

    private static int mockServerPort;
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {
        mockServerPort = new ProxyBuilder().withLocalPort(0).build().getPort();
        mockServerClient = new MockServerClient("localhost", mockServerPort);

        echoServer = new EchoServer(false);
    }

    @AfterClass
    public static void stopServer() {
        mockServerClient.stop();

        echoServer.stop();
    }

    @Override
    public int getMockServerPort() {
        return mockServerPort;
    }

    @Override
    public int getMockServerSecurePort() {
        return mockServerPort;
    }

    @Override
    public int getTestServerPort() {
        return echoServer.getPort();
    }
}
