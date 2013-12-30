package org.mockserver.maven;

import org.mockserver.server.MockServerRunner;

import java.util.concurrent.Future;

/**
 * @author jamesdbloom
 */
public class EmbeddedJettyHolder {

    private static final MockServerRunner MOCK_SERVER_RUNNER = new MockServerRunner();

    public Future start(final int port, final int securePort, final String logLevel) {
        if (!MOCK_SERVER_RUNNER.isRunning()) {
            MockServerRunner.overrideLogLevel(logLevel);
            return MOCK_SERVER_RUNNER.start((port != -1 ? port : null), (securePort != -1 ? securePort : null));
        } else {
            throw new IllegalStateException("MockServer is already running!");
        }
    }

    public void stop() {
        if (MOCK_SERVER_RUNNER.isRunning()) {
            MOCK_SERVER_RUNNER.stop();
        } else {
            throw new IllegalStateException("MockServer is not running!");
        }
    }
}
