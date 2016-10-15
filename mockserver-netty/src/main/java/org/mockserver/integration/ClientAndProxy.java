package org.mockserver.integration;

import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.ProxyBuilder;

/**
 * @author jamesdbloom
 */
public class ClientAndProxy extends ProxyClient {

    private final Proxy httpProxy;

    public ClientAndProxy(Integer port) {
        super("localhost", port);
        httpProxy = new ProxyBuilder().withLocalPort(port).build();
    }

    public ClientAndProxy(Integer port, String remoteHost, Integer remotePort) {
        super("localhost", port);
        httpProxy = new ProxyBuilder().withLocalPort(port).withDirect(remoteHost, remotePort).build();
    }

    public static ClientAndProxy startClientAndProxy(Integer port) {
        return new ClientAndProxy(port);
    }

    public static ClientAndProxy startClientAndDirectProxy(Integer port, String remoteHost, Integer remotePort) {
        return new ClientAndProxy(port, remoteHost, remotePort);
    }

    public boolean isRunning() {
        return httpProxy.isRunning();
    }
}
