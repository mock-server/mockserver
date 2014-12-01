package org.mockserver.proxy;

import org.mockserver.proxy.direct.DirectProxy;
import org.mockserver.proxy.http.HttpProxy;

/**
 * This class should be used to configure the HttpProxy, using this class is the simplest way to create an HttpProxy instance
 *
 * @author jamesdbloom
 */
public class ProxyBuilder {

    private Integer localPort;
    private String remoteHost;
    private Integer remotePort;

    /**
     * Configure the local port for the proxy, this will be the same port for all traffic including HTTP, SOCKS, CONNECT and SSL
     *
     * @param localPort the local port to use
     */
    public ProxyBuilder withLocalPort(Integer localPort) {
        this.localPort = localPort;
        return this;
    }

    /**
     * Configure a direct proxy that forwards all requests from the localPort to the remoteHost and remotePort
     *
     * @param remoteHost the destination hostname for direct forwarding
     * @param remotePort the destination port for direct forwarding
     */
    public ProxyBuilder withDirect(String remoteHost, Integer remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        return this;
    }

    /**
     * Build an instance of the HttpProxy
     */
    public Proxy build() {
        if (localPort != null) {
            if (remoteHost != null && remotePort != null) {
                return new DirectProxy(localPort, remoteHost, remotePort);
            } else {
                return new HttpProxy(localPort);
            }
        } else {
            throw new IllegalArgumentException("LocalPort must be specified before the proxy is started");
        }
    }

}
