package org.mockserver.jetty.proxy;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.integration.testserver.TestServer;
import org.mockserver.jetty.cli.Main;
import org.mockserver.configuration.SystemProperties;
import org.mockserver.integration.proxy.AbstractClientSecureProxyIntegrationTest;
import org.mockserver.jetty.server.MockServerRunner;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ProxyCommandLineIntegrationTest extends AbstractClientSecureProxyIntegrationTest {

    private final static int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTP_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTPS_PORT = PortFactory.findFreePort();
    private static TestServer testServer = new TestServer();

    @BeforeClass
    public static void setupFixture() throws Exception {
        // start server
        testServer.startServer(SERVER_HTTP_PORT, SERVER_HTTPS_PORT);

        // start proxy
        Main.reset();
        Main.main("-proxyPort", "" + PROXY_HTTP_PORT, "-proxySecurePort", "" + PROXY_HTTPS_PORT);
        TimeUnit.MILLISECONDS.sleep(500);
    }

    @AfterClass
    public static void stopFixture() throws Exception {
        // stop server
        testServer.stop();

        // stop proxy
        new MockServerRunner().stop("127.0.0.1", SystemProperties.serverStopPort(PROXY_HTTP_PORT, PROXY_HTTPS_PORT), 5);
        TimeUnit.MILLISECONDS.sleep(500);
    }

    @Override
    public int getProxyPort() {
        return PROXY_HTTP_PORT;
    }

    @Override
    public int getServerPort() {
        return SERVER_HTTP_PORT;
    }

    @Override
    public int getServerSecurePort() {
        return SERVER_HTTPS_PORT;
    }
}
