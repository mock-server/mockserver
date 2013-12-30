package org.mockserver.proxy;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.cli.Main;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;
import org.mockserver.server.MockServerRunner;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ClientProxyEmbeddedJettyCommandLineIntegrationTest extends AbstractClientProxyIntegrationTest {

    private final static int proxyPort = 1080;

    @BeforeClass
    public static void startProxy() throws InterruptedException {
        Main.main("-proxyPort", "" + proxyPort);
        // wait for server to start up
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    }

    @AfterClass
    public static void stopProxy() {
        new MockServerRunner().stop("127.0.0.1", proxyPort + 1, "STOP_KEY", 500);
    }

    @Before
    public void clearServer() throws InterruptedException {
        proxyClient.reset();
    }

    @Override
    public int getPort() {
        return proxyPort;
    }

}
