package org.mockserver.proxy;

import org.junit.After;
import org.junit.Before;
import org.mockserver.integration.proxy.AbstractClientProxyIntegrationTest;

/**
 * @author jamesdbloom
 */
public class ClientProxyEmbeddedJettyAPIIntegrationTest extends AbstractClientProxyIntegrationTest {

    private ProxyRunner proxyRunner;
    private final int proxyPort = 1080;

    @Before
    public void startProxy() {
        proxyRunner = new ProxyRunner();
        proxyRunner.start(proxyPort);
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
