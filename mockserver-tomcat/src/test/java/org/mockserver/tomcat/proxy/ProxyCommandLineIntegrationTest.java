package org.mockserver.tomcat.proxy;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockserver.configuration.SystemProperties;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.integration.proxy.ServerRunner;
import org.mockserver.socket.PortFactory;
import org.mockserver.tomcat.cli.Main;
import org.mockserver.tomcat.server.MockServerRunner;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ProxyCommandLineIntegrationTest extends AbstractClientProxyIntegrationTest {

    private final static int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTP_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTPS_PORT = PortFactory.findFreePort();
    private final static ServerRunner serverRunner = new ServerRunner();

    @BeforeClass
    public static void startServer() throws Exception {
        serverRunner.startServer(SERVER_HTTP_PORT, SERVER_HTTPS_PORT);
        // wait for server to start up
        Thread.sleep(TimeUnit.MILLISECONDS.toMillis(500));
    }

    @BeforeClass
    public static void startProxy() throws InterruptedException {
        Main.reset();
        Main.main("-proxyPort", "" + PROXY_HTTP_PORT, "-proxySecurePort", "" + PROXY_HTTPS_PORT);
        // wait for proxy server to start up
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    }

    @AfterClass
    public static void stopProxy() throws Exception {
        new MockServerRunner().stop("127.0.0.1", SystemProperties.serverStopPort(PROXY_HTTP_PORT, PROXY_HTTPS_PORT), 5);
        // wait for proxy server to shutdown
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    }

    @AfterClass
    public static void stopServer() throws Exception {
        serverRunner.stopServer();
        // wait for server to shutdown
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
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
