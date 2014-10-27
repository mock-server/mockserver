package org.mockserver;

import org.junit.Test;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.client.server.MockServerClient;

import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
public class ClientServerMavenPluginStopTest {

    @Test(expected = RuntimeException.class)
    public void shouldNotBeAbleToReachMockServer() throws InterruptedException {
        new MockServerClient("127.0.0.1", 8080).reset();
    }

}
