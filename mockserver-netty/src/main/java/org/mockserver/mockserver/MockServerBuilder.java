package org.mockserver.mockserver;

import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.stop.StopEventQueue;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class MockServerBuilder {

    private Integer[] port;
    private StopEventQueue stopEventQueue = new StopEventQueue();

    public MockServerBuilder withStopEventQueue(StopEventQueue stopEventQueue) {
        this.stopEventQueue = stopEventQueue;
        return this;
    }

    /**
     * Configure HTTP and HTTPS ports
     *
     * @param ports the HTTP ports to use
     */
    public MockServerBuilder withHTTPPort(Integer... ports) {
        if (ports != null && ports.length >= 1) {
            ConfigurationProperties.mockServerPort(ports);
            this.port = ports;
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
