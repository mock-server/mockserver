package org.mockserver.integration.proxy.http;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.mockserver.MockServer;

import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class NettyHttpProxyIntegrationTest extends AbstractClientProxyIntegrationTest {

    private static int mockServerPort;
    private static EchoServer echoServer;
    private static MockServerClient mockServerClient;

    @BeforeClass
    public static void setupFixture() {
        servletContext = "";

        mockServerPort = new MockServer().getLocalPort();
        mockServerClient = new MockServerClient("localhost", mockServerPort);

        echoServer = new EchoServer(false);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(echoServer);
        stopQuietly(mockServerClient);
    }

    @Before
    public void resetProxy() {
        System.out.println("mockServerPort reset start = " + mockServerPort);
        mockServerClient.reset();
        System.out.println("mockServerPort reset end = " + mockServerPort);
    }

    @Override
    public int getProxyPort() {
        return mockServerPort;
    }

    @Override
    public int getSecureProxyPort() {
        return mockServerPort;
    }

    @Override
    public MockServerClient getMockServerClient() {
        return mockServerClient;
    }

    @Override
    public int getServerPort() {
        return echoServer.getPort();
    }
}
