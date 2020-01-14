package org.mockserver.netty.integration.proxy.http;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.testing.integration.proxy.AbstractClientSecureProxyIntegrationTest;
import org.mockserver.netty.MockServer;

import static org.mockserver.stop.Stop.stopQuietly;

/**
 * @author jamesdbloom
 */
public class NettyHttpProxySecureIntegrationTest extends AbstractClientSecureProxyIntegrationTest {

    private static int mockServerPort;
    private static EchoServer echoServer;
    private static MockServerClient mockServerClient;

    @BeforeClass
    public static void setupFixture() {
        mockServerPort = new MockServer().getLocalPort();
        mockServerClient = new MockServerClient("localhost", mockServerPort);

        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", String.valueOf(mockServerPort));

        echoServer = new EchoServer(true);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(echoServer);

        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");

        stopQuietly(mockServerClient);
    }

    @Before
    public void resetProxy() {
        mockServerClient.reset();
        echoServer.mockServerEventLog().reset();
    }

    @Override
    public int getProxyPort() {
        return mockServerPort;
    }

    @Override
    public MockServerClient getProxyClient() {
        return mockServerClient;
    }

    @Override
    public int getServerSecurePort() {
        return echoServer.getPort();
    }
}
