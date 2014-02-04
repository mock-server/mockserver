package org.mockserver.mockserver;

import org.mockserver.proxy.http.HttpProxy;

/**
 * @author jamesdbloom
 */
public class MockServerBuilder {

    private Integer port;
    private Integer securePort;

    /**
     * Configure HTTP port for proxy, setting this value will ensure HTTP is supported
     *
     * @param port the HTTP port to use
     */
    public MockServerBuilder withHTTPPort(Integer port) {
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
    public MockServerBuilder withHTTPSPort(Integer securePort) {
        if (securePort != null && securePort != -1) {
            this.securePort = securePort;
        } else {
            this.securePort = null;
        }
        return this;
    }

    /**
     * Build an instance of the HttpProxy
     */
    public MockServer build() {
        return new MockServer(port, securePort);
    }
}
