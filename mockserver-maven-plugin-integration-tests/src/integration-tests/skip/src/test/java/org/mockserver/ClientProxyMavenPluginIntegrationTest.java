package org.mockserver;

import org.junit.Test;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.client.server.MockServerClient;

import java.net.ConnectException;

/**
 * @author jamesdbloom
 */
public class ClientProxyMavenPluginIntegrationTest {

    @Test(expected = RuntimeException.class)
    public void shouldNotBeAbleToReachProxy() {
        new ProxyClient("127.0.0.1", 9090).reset();
    }

    @Test(expected = RuntimeException.class)
    public void shouldNotBeAbleToReachMockServer() {
        new MockServerClient("127.0.0.1", 8080).reset();
    }
}
