package org.mockserver.integration.mocking;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.netty.proxy.ProxyConfiguration;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.server.AbstractBasicMockingIntegrationTest;
import org.mockserver.mockserver.MockServer;

import static org.mockserver.client.netty.proxy.ProxyConfiguration.proxyConfiguration;

/**
 * @author jamesdbloom
 */
//@Ignore
public class ForwardViaSocksProxyMockingIntegrationTest extends AbstractBasicMockingIntegrationTest {

    private static MockServer mockServer;
    private static MockServer proxy;
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {
        proxy = new MockServer();

        mockServer = new MockServer(proxyConfiguration(ProxyConfiguration.Type.SOCKS5, "127.0.0.1:" + String.valueOf(proxy.getLocalPort())));

        echoServer = new EchoServer(false);

        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort(), servletContext);
    }

    @AfterClass
    public static void stopServer() {
        if (proxy != null) {
            proxy.stop();
        }

        if (mockServer != null) {
            mockServer.stop();
        }

        if (echoServer != null) {
            echoServer.stop();
        }
    }

    @Override
    public int getMockServerPort() {
        return mockServer.getLocalPort();
    }

    @Override
    public int getMockServerSecurePort() {
        return mockServer.getLocalPort();
    }

    @Override
    public int getTestServerPort() {
        return echoServer.getPort();
    }
}
