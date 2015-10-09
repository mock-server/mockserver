package org.mockserver.mockserver;

import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.stop.StopEventQueue;

/**
 * @author jamesdbloom
 */
public class MockServerBuilder {

    private Integer port;
    private StopEventQueue stopEventQueue = new StopEventQueue();

    public MockServerBuilder withStopEventQueue(StopEventQueue stopEventQueue) {
        this.stopEventQueue = stopEventQueue;
        return this;
    }

    /**
     * Configure HTTP and HTTPS port
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
     * Build an instance of the MockServer
     */
    public MockServer build() {
        return new MockServer(port).withStopEventQueue(stopEventQueue);
    }
}
