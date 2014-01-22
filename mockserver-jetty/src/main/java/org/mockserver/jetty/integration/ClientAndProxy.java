package org.mockserver.jetty.integration;

import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.jetty.proxy.ProxyRunner;

/**
 * @author jamesdbloom
 */
public class ClientAndProxy extends ProxyClient {

    private final ProxyRunner proxyRunner;

    public ClientAndProxy(Integer port) {
        super("localhost", port);
        proxyRunner = new ProxyRunner().start(port, null);
    }

    public ClientAndProxy(Integer port, Integer securePort) {
        super("localhost", port);
        proxyRunner = new ProxyRunner().start(port, securePort);
    }

    public static ClientAndProxy startClientAndProxy(Integer port) {
        return new ClientAndProxy(port);
    }

    public static ClientAndProxy startClientAndProxy(Integer port, Integer securePort) {
        return new ClientAndProxy(port, securePort);
    }

    public void stop() {
        proxyRunner.stop();
    }

    public boolean isRunning() {
        return proxyRunner.isRunning();
    }
}
