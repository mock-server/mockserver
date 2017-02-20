package org.mockserver.integration.proxy.http;

import org.junit.Test;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.socket.PortFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class StopClientProxyNettyIntegrationTest {

    private final static int serverPort = PortFactory.findFreePort();

    @Test
    public void canStartAndStopMultipleTimesViaClient() {
        // start server
        new ProxyBuilder().withLocalPort(serverPort).build();

        // start client
        ProxyClient proxyClient = new ProxyClient("localhost", serverPort);

        for (int i = 0; i < 2; i++) {
            // when
            proxyClient.stop();

            // then
            assertFalse(proxyClient.isRunning());
            new ProxyBuilder().withLocalPort(serverPort).build();
            assertTrue(proxyClient.isRunning());
        }

        assertTrue(proxyClient.isRunning());
        proxyClient.stop();
        assertFalse(proxyClient.isRunning());
    }

    @Test
    public void canStartAndStopMultipleTimes() {
        // start server
        Proxy httpProxy = new ProxyBuilder().withLocalPort(serverPort).build();

        for (int i = 0; i < 2; i++) {
            // when
            httpProxy.stop();

            // then
            assertFalse(httpProxy.isRunning());
            httpProxy = new ProxyBuilder().withLocalPort(serverPort).build();
            assertTrue(httpProxy.isRunning());
        }

        assertTrue(httpProxy.isRunning());
        httpProxy.stop();
        assertFalse(httpProxy.isRunning());
    }
}
