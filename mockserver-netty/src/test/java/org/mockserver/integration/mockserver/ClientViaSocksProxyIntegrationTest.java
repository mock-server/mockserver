package org.mockserver.integration.mockserver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.client.netty.proxy.ProxyConfiguration;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.integration.server.AbstractBasicClientServerIntegrationTest;
import org.mockserver.mockserver.MockServer;

import static org.mockserver.client.netty.proxy.ProxyConfiguration.proxyConfiguration;

/**
 * @author jamesdbloom
 */
//@Ignore
public class ClientViaSocksProxyIntegrationTest extends AbstractBasicClientServerIntegrationTest {

    private static MockServer mockServer;
    private static MockServer proxy;
    private static EchoServer echoServer;

    @BeforeClass
    public static void startServer() {
        proxy = new MockServer();

        mockServer = new MockServer(proxyConfiguration(ProxyConfiguration.Type.SOCKS5, "127.0.0.1:" + String.valueOf(proxy.getPort())));

        echoServer = new EchoServer(false);

        mockServerClient = new MockServerClient("localhost", mockServer.getPort(), servletContext);
    }

    @AfterClass
    public static void stopServer() {
        proxy.stop();

        mockServer.stop();

        echoServer.stop();
    }

    @Override
    public int getMockServerPort() {
        return mockServer.getPort();
    }

    @Override
    public int getMockServerSecurePort() {
        return mockServer.getPort();
    }

    @Override
    public int getTestServerPort() {
        return echoServer.getPort();
    }
}
