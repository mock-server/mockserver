package org.mockserver.maven;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.NettyMockServer;
import org.mockserver.proxy.http.HttpProxy;

/**
 * @author jamesdbloom
 */
public class InstanceHolder {

    @VisibleForTesting
    static HttpProxy proxy = new HttpProxy();
    @VisibleForTesting
    static NettyMockServer mockServer = new NettyMockServer();

    public void start(final int mockServerPort, final int mockServerSecurePort, final int proxyPort, final int proxySecurePort, final String logLevel) {
        mockServer.overrideLogLevel(logLevel);
        if (!mockServer.isRunning()) {
            if (mockServerPort != -1 || mockServerSecurePort != -1) {
                mockServer.start((mockServerPort != -1 ? mockServerPort : null), (mockServerSecurePort != -1 ? mockServerSecurePort : null));
            }
        } else {
            throw new IllegalStateException("MockServer is already running!");
        }
        if (!proxy.isRunning()) {
            if (proxyPort != -1 || proxySecurePort != -1) {
                proxy.startHttpProxy((proxyPort != -1 ? proxyPort : null), (proxySecurePort != -1 ? proxySecurePort : null));
            }
        } else {
            throw new IllegalStateException("Proxy is already running!");
        }

    }

    public void stop(final int mockServerPort, final int proxyPort, final String logLevel) {
        mockServer.overrideLogLevel(logLevel);
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
        if (mockServer.isRunning()) {
            mockServer.stop();
        }
        if (proxy.isRunning()) {
            proxy.stop();
        }
    }
}
