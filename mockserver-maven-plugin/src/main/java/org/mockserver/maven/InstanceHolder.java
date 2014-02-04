package org.mockserver.maven;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.MockServer;
import org.mockserver.mockserver.MockServerBuilder;
import org.mockserver.proxy.http.HttpProxy;
import org.mockserver.proxy.http.HttpProxyBuilder;

/**
 * @author jamesdbloom
 */
public class InstanceHolder {

    private HttpProxy proxy;
    private MockServer mockServer;

    @VisibleForTesting
    static HttpProxyBuilder proxyBuilder = new HttpProxyBuilder();
    @VisibleForTesting
    static MockServerBuilder mockServerBuilder = new MockServerBuilder();

    public void start(final int mockServerPort, final int mockServerSecurePort, final int proxyPort, final int proxySecurePort) {
        if (mockServer == null || !mockServer.isRunning()) {
            if (mockServerPort != -1 || mockServerSecurePort != -1) {
                mockServer = mockServerBuilder.withHTTPPort(mockServerPort).withHTTPSPort(mockServerSecurePort).build();
            }
        } else {
            throw new IllegalStateException("MockServer is already running!");
        }
        if (proxy == null || !proxy.isRunning()) {
            if (proxyPort != -1 || proxySecurePort != -1) {
                proxy = proxyBuilder.withHTTPPort(proxyPort).withHTTPSPort(proxySecurePort).build();
            }
        } else {
            throw new IllegalStateException("Proxy is already running!");
        }

    }

    public void stop(final int mockServerPort, final int proxyPort) {
        if (mockServerPort != -1) {
            newMockServerClient(mockServerPort).stop();
        }
        if (proxyPort != -1) {
            newProxyClient(proxyPort).stop();
        }
    }

    @VisibleForTesting
    ProxyClient newProxyClient(int proxyStopPort) {
        return new ProxyClient("127.0.0.1", proxyStopPort);
    }

    @VisibleForTesting
    MockServerClient newMockServerClient(int mockServerPort) {
        return new MockServerClient("127.0.0.1", mockServerPort);
    }

    public void stop() {
        if (mockServer != null && mockServer.isRunning()) {
            mockServer.stop();
        }
        if (proxy != null && proxy.isRunning()) {
            proxy.stop();
        }
    }
}
