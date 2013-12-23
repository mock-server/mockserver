package org.mockserver.proxy;

import org.junit.After;
import org.junit.Before;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;

/**
 * @author jamesdbloom
 */
public class ClientProxyEmbeddedJettyAPIIntegrationTest extends AbstractClientProxyIntegrationTest {

    private final int proxyPort = 1080;
    private ProxyRunner proxyRunner;

    @Before
    public void startProxy() {
        proxyRunner = new ProxyRunner();
        proxyRunner.start(proxyPort, proxyPort - 1);
    }

    @Override
    public int getPort() {
        return proxyPort;
    }

    @After
    public void stopProxy() {
        proxyRunner.stop();
    }
}
