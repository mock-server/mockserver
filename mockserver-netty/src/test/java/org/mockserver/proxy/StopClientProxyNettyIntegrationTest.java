package org.mockserver.proxy;

import org.junit.Test;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.proxy.http.HttpProxy;
import org.mockserver.proxy.http.HttpProxyBuilder;
import org.mockserver.socket.PortFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class StopClientProxyNettyIntegrationTest {

    private final static int serverPort = PortFactory.findFreePort();
    private final static int serverSecurePort = PortFactory.findFreePort();

    @Test
    public void canStartAndStopMultipleTimes() {
        // start server
        HttpProxy httpProxy = new HttpProxyBuilder().withHTTPPort(serverPort).withHTTPSPort(serverSecurePort).build();

        // start client
        ProxyClient proxyClient = new ProxyClient("localhost", serverPort);

        for (int i = 0; i < 5; i++) {
            // when
            proxyClient.stop();

            // then
            assertFalse(httpProxy.isRunning());
            httpProxy = new HttpProxyBuilder().withHTTPPort(serverPort).withHTTPSPort(serverSecurePort).build();
            assertTrue(httpProxy.isRunning());
        }

        assertTrue(httpProxy.isRunning());
        httpProxy.stop();
        assertFalse(httpProxy.isRunning());
    }
}
