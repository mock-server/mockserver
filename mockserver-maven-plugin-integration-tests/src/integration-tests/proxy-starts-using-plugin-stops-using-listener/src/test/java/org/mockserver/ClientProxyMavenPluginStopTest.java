package org.mockserver;

import org.junit.Test;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.client.server.MockServerClient;

import java.net.ConnectException;

/**
 * @author jamesdbloom
 */
public class ClientProxyMavenPluginStopTest {

    @Test(expected = RuntimeException.class)
    public void shouldNotBeAbleToReachProxy() {
        new ProxyClient("127.0.0.1", 9098).reset();
    }
}
