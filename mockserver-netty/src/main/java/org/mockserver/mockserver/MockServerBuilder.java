package org.mockserver.mockserver;

import org.mockserver.configuration.ConfigurationProperties;

/**
 * @author jamesdbloom
 */
public class MockServerBuilder {

    private Integer port;

    private String path;

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

    public MockServerBuilder withDatabase(String path) {
        if (path != null) {
            this.path = path;
        } else {
            this.path = null;
        }
        return this;
    }

    /**
     * Build an instance of the HttpProxy
     */
    public MockServer build() {
        return new MockServer(port, path);
    }
}
