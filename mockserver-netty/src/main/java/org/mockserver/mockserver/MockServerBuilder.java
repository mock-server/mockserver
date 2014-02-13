package org.mockserver.mockserver;

import com.google.common.annotations.VisibleForTesting;

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
        MockServer mockServer = newMockServer();
        mockServer.start(port, securePort);
        return mockServer;
    }

    /**
     * Build an instance of the HttpProxy
     */
    public Thread buildAndReturnThread() {
        return newMockServer()
                .start(
                        port,
                        securePort
                );
    }

    @VisibleForTesting
    MockServer newMockServer() {
        return new MockServer();
    }
}
