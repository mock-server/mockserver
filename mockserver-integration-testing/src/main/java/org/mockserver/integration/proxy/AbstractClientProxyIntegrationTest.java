package org.mockserver.integration.proxy;

import org.mockserver.client.proxy.ProxyClient;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientProxyIntegrationTest {

    protected ProxyClient proxyClient;

    public abstract int getPort();
}
