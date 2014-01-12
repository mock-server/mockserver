package org.mockserver.maven;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.server.MockServerRunner;

import java.util.concurrent.Future;

/**
 * @author jamesdbloom
 */
public class EmbeddedJettyHolder {

    @VisibleForTesting
    static MockServerRunner MOCK_SERVER_RUNNER = new MockServerRunner();

    public MockServerRunner start(final int port, final int securePort, final String logLevel) {
        if (!MOCK_SERVER_RUNNER.isRunning()) {
            MOCK_SERVER_RUNNER.overrideLogLevel(logLevel);
            return MOCK_SERVER_RUNNER.start((port != -1 ? port : null), (securePort != -1 ? securePort : null));
        } else {
            throw new IllegalStateException("MockServer is already running!");
        }
    }

    public boolean stop(final int stopPort, final int stopWait, final String logLevel) {
        MOCK_SERVER_RUNNER.overrideLogLevel(logLevel);
        return MOCK_SERVER_RUNNER.stop("127.0.0.1", stopPort, stopWait);
    }

    public void stop() {
        if (MOCK_SERVER_RUNNER.isRunning()) {
            MOCK_SERVER_RUNNER.stop();
        } else {
            throw new IllegalStateException("MockServer is not running!");
        }
    }
}
