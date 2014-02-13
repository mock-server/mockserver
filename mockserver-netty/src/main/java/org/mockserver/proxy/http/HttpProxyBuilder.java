package org.mockserver.proxy.http;

import com.google.common.annotations.VisibleForTesting;

/**
 * This class should be used to configure the HttpProxy, using this class is the simplest way to create an HttpProxy instance
 *
 * @author jamesdbloom
 */
public class HttpProxyBuilder {

    private Integer port;
    private Integer securePort;
    private Integer socksPort;
    private Integer directLocalPort;
    private Integer directLocalSecurePort;
    private String directRemoteHost;
    private Integer directRemotePort;

    /**
     * Configure HTTP port for proxy, setting this value will ensure HTTP is supported
     *
     * @param port the HTTP port to use
     */
    public HttpProxyBuilder withHTTPPort(Integer port) {
        if (port != null && port != -1) {
            this.port = port;
        } else {
            this.port = null;
        }
        return this;
    }

    /**
     * Configure HTTP/SSL (HTTPS) port for proxy, setting this value will ensure HTTPS is supported,
     * clients that use HTTP -> HTTPS CONNECT request also require the HTTP port to be set using HttpProxyBuilder#withHTTPPort(Integer port)
     *
     * @param securePort the HTTP/SSL (HTTPS) port to use
     */
    public HttpProxyBuilder withHTTPSPort(Integer securePort) {
        if (securePort != null && securePort != -1) {
            this.securePort = securePort;
        } else {
            this.securePort = null;
        }
        return this;
    }

    /**
     * Configure SOCKS port for proxy, setting this value will ensure SOCKS is supported
     * Note: currently clients using SSL after connecting with SOCKS are not current supported
     *
     * @param socksPort the SOCKS port to use
     */
    public HttpProxyBuilder withSOCKSPort(Integer socksPort) {
        if (socksPort != null && socksPort != -1) {
            this.socksPort = socksPort;
        } else {
            this.socksPort = null;
        }
        return this;
    }

    /**
     * Configure a direct proxy that forwards all requests on the fromPort to the toHost and toPort
     *
     * @param fromPort the local proxy port for direct forwarding
     * @param toHost the destination hostname for direct forwarding
     * @param toPort the destination port for direct forwarding
     */
    public HttpProxyBuilder withDirect(Integer fromPort, String toHost, Integer toPort) {
        if (fromPort != null && fromPort != -1) {
            this.directLocalPort = fromPort;
            this.directRemoteHost = toHost;
            this.directRemotePort = toPort;
        } else {
            this.directLocalPort = null;
        }
        return this;
    }

    /**
     * Configure a secure direct proxy that forwards all requests on the fromSecurePort to the toHost and toPort using SSL
     *
     * @param fromSecurePort the local proxy port for direct forwarding over SSL
     * @param toHost the destination hostname for direct forwarding
     * @param toPort the destination port for direct forwarding
     */
    public HttpProxyBuilder withDirectSSL(Integer fromSecurePort, String toHost, Integer toPort) {
        if (fromSecurePort != null && fromSecurePort != -1) {
            this.directLocalSecurePort = fromSecurePort;
            this.directRemoteHost = toHost;
            this.directRemotePort = toPort;
        } else {
            this.directLocalSecurePort = null;
        }
        return this;
    }

    /**
     * Build an instance of the HttpProxy
     */
    public HttpProxy build() {
        HttpProxy httpProxy = newHttpProxy();
        httpProxy.start(
                port,
                securePort,
                socksPort,
                directLocalPort,
                directLocalSecurePort,
                directRemoteHost,
                directRemotePort
        );
        return httpProxy;
    }

    /**
     * Build an instance of the HttpProxy
     */
    public Thread buildAndReturnThread() {
        return newHttpProxy()
                .start(
                        port,
                        securePort,
                        socksPort,
                        directLocalPort,
                        directLocalSecurePort,
                        directRemoteHost,
                        directRemotePort
                );
    }

    @VisibleForTesting
    HttpProxy newHttpProxy() {
        return new HttpProxy();
    }
}
