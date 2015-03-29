package org.mockserver.mockserver;

import org.mockserver.configuration.ConfigurationProperties;

/**
 * @author jamesdbloom
 */
public class MockServerBuilder {

    private Integer port;

    /**
     * Configure HTTP port for proxy, setting this value will ensure HTTP is supported
     *
     * @param port the HTTP port to use
     */
    public MockServerBuilder withHTTPPort(Integer port) {
        if (port != null && port != -1) {
            ConfigurationProperties.mockServerPort(port);
            this.port = port;
        } else {
            this.port = null;
        }
        return this;
    }

    /**
     * Build an instance of the HttpProxy
     */
    public MockServer build() {
        return new MockServer(port);
    }
}
