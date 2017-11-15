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

    public static void runInitializationClass(Integer[] mockServerPorts, ExpectationInitializer expectationInitializer) {
        if (mockServerPorts != null && mockServerPorts.length > 0 && expectationInitializer != null) {
            expectationInitializer.initializeExpectations(getMockServerClient(mockServerPorts[0]));
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

    public void start(final Integer[] mockServerPorts, final Integer proxyPort, ExpectationInitializer expectationInitializer) {
        if (mockServer == null || !mockServer.isRunning()) {
            if (mockServerPorts != null && mockServerPorts.length > 0) {
                mockServer = mockServerBuilder.withHTTPPort(mockServerPorts).build();
            }
            runInitializationClass(mockServerPorts, expectationInitializer);
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

    public void stop(final Integer[] mockServerPorts, final int proxyPort, boolean ignoreFailure) {
        if (mockServerPorts != null && mockServerPorts.length > 0) {
            getMockServerClient(mockServerPorts[0]).stop(ignoreFailure);
        }
        if (proxyPort != -1) {
            getProxyClient(proxyPort).stop(ignoreFailure);
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
