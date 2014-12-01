package org.mockserver.maven;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.initialize.ExpectationInitializer;
import org.mockserver.mockserver.MockServer;
import org.mockserver.mockserver.MockServerBuilder;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.ProxyBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jamesdbloom
 */
public class InstanceHolder extends ObjectWithReflectiveEqualsHashCodeToString {

    @VisibleForTesting
    static Map<Integer, MockServerClient> mockServerClients = new ConcurrentHashMap<Integer, MockServerClient>();
    @VisibleForTesting
    static Map<Integer, ProxyClient> proxyClients = new ConcurrentHashMap<Integer, ProxyClient>();
    private ProxyBuilder proxyBuilder = new ProxyBuilder();
    private MockServerBuilder mockServerBuilder = new MockServerBuilder();
    private Proxy proxy;
    private MockServer mockServer;

    public static void runInitializationClass(int mockServerPort, ExpectationInitializer expectationInitializer) {
        if (mockServerPort != -1 && expectationInitializer != null) {
            expectationInitializer.initializeExpectations(getMockServerClient(mockServerPort));
        }
    }

    private static ProxyClient getProxyClient(int proxyPort) {
        if (!proxyClients.containsKey(proxyPort)) {
            proxyClients.put(proxyPort, new ProxyClient("127.0.0.1", proxyPort));
        }
        return proxyClients.get(proxyPort);
    }

    private static MockServerClient getMockServerClient(int mockServerPort) {
        if (!mockServerClients.containsKey(mockServerPort)) {
            mockServerClients.put(mockServerPort, new MockServerClient("127.0.0.1", mockServerPort));
        }
        return mockServerClients.get(mockServerPort);
    }

    public void start(final int mockServerPort, final int mockServerSecurePort, final int proxyPort, ExpectationInitializer expectationInitializer) {
        if (mockServer == null || !mockServer.isRunning()) {
            if (mockServerPort != -1 || mockServerSecurePort != -1) {
                mockServer = mockServerBuilder.withHTTPPort(mockServerPort).withHTTPSPort(mockServerSecurePort).build();
            }
            runInitializationClass(mockServerPort, expectationInitializer);
        } else {
            throw new IllegalStateException("MockServer is already running!");
        }
        if (proxy == null || !proxy.isRunning()) {
            if (proxyPort != -1) {
                proxy = proxyBuilder.withLocalPort(proxyPort).build();
            }
        } else {
            throw new IllegalStateException("Proxy is already running!");
        }
    }

    public void stop(final int mockServerPort, final int proxyPort) {
        if (mockServerPort != -1) {
            getMockServerClient(mockServerPort).stop();
        }
        if (proxyPort != -1) {
            getProxyClient(proxyPort).stop();
        }
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
