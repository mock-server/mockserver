package org.mockserver.integration;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.cli.Main;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.socket.PortFactory;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class MockServerAndProxyStopTest {

    private int SERVER_HTTP_PORT;
    private Integer PROXY_HTTP_PORT;

    @Before
    public void generatePorts() {
        SERVER_HTTP_PORT = PortFactory.findFreePort();
        PROXY_HTTP_PORT = PortFactory.findFreePort();
    }

    @Test
    public void shouldStopMockServerAndProxyByMockServerClient() throws InterruptedException {
        // given
        Main.main("-serverPort", "" + SERVER_HTTP_PORT, "-proxyPort", "" + PROXY_HTTP_PORT);
        MockServerClient mockServerClient = new MockServerClient("localhost", SERVER_HTTP_PORT);

        // and
        assertTrue(mockServerClient.isRunning());

        // when
        mockServerClient.stop();

        // then
        assertFalse(mockServerClient.isRunning());
    }

    @Test
    public void shouldStopMockServerAndProxyByProxyClient() throws InterruptedException {
        // given
        Main.main("-serverPort", "" + SERVER_HTTP_PORT, "-proxyPort", "" + PROXY_HTTP_PORT);
        ProxyClient proxyClient = new ProxyClient("localhost", PROXY_HTTP_PORT);

        // and
        assertTrue(proxyClient.isRunning());

        // when
        proxyClient.stop();

        // then
        assertFalse(proxyClient.isRunning());
    }

    @Test
    public void shouldStopMockServerOnly() throws InterruptedException {
        // given
        Main.main("-serverPort", "" + SERVER_HTTP_PORT);
        MockServerClient mockServerClient = new MockServerClient("localhost", SERVER_HTTP_PORT);

        // and
        assertTrue(mockServerClient.isRunning());

        // when
        mockServerClient.stop();

        // then
        assertFalse(mockServerClient.isRunning());
    }

    @Test
    public void shouldStopProxyOnly() throws InterruptedException {
        // given
        Main.main("-proxyPort", "" + PROXY_HTTP_PORT);
        ProxyClient proxyClient = new ProxyClient("localhost", PROXY_HTTP_PORT);

        // and
        assertTrue(proxyClient.isRunning());

        // when
        proxyClient.stop();

        // then
        assertFalse(proxyClient.isRunning());
    }
}
