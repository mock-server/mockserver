package org.mockserver.integration;

import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.proxy.http.HttpProxy;
import org.mockserver.proxy.http.HttpProxyBuilder;

/**
 * @author jamesdbloom
 */
public class ClientAndProxy extends ProxyClient {

    private final HttpProxy httpProxy;

    public ClientAndProxy(Integer port) {
        super("localhost", port);
        httpProxy = new HttpProxyBuilder().withHTTPPort(port).build();
    }

    public ClientAndProxy(Integer port, Integer securePort) {
        super("localhost", port);
        httpProxy = new HttpProxyBuilder().withHTTPPort(port).withHTTPSPort(securePort).build();
    }

    public static ClientAndProxy startClientAndProxy(Integer port) {
        return new ClientAndProxy(port);
    }

    public static ClientAndProxy startClientAndProxy(Integer port, Integer securePort) {
        return new ClientAndProxy(port, securePort);
    }

    public boolean isRunning() {
        return httpProxy.isRunning();
    }
}
