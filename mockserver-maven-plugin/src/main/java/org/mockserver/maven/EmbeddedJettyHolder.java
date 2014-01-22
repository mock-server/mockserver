package org.mockserver.maven;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.jetty.proxy.ProxyRunner;
import org.mockserver.jetty.runner.AbstractRunner;
import org.mockserver.jetty.server.MockServerRunner;

/**
 * @author jamesdbloom
 */
public class EmbeddedJettyHolder {

    @VisibleForTesting
    static MockServerRunner MOCK_SERVER_RUNNER = new MockServerRunner();
    @VisibleForTesting
    static ProxyRunner PROXY_RUNNER = new ProxyRunner();

    public AbstractRunner start(final int mockServerPort, final int mockServerSecurePort, final int proxyPort, final int proxySecurePort, final String logLevel) {
        MOCK_SERVER_RUNNER.overrideLogLevel(logLevel);
        AbstractRunner abstractRunner = null;
        if (!PROXY_RUNNER.isRunning()) {
            if (proxyPort != -1 || proxySecurePort != -1) {
                abstractRunner = PROXY_RUNNER.start((proxyPort != -1 ? proxyPort : null), (proxySecurePort != -1 ? proxySecurePort : null));
            }
        } else {
            throw new IllegalStateException("Proxy is already running!");
        }
        if (!MOCK_SERVER_RUNNER.isRunning()) {
            if (mockServerPort != -1 || mockServerSecurePort != -1) {
                abstractRunner = MOCK_SERVER_RUNNER.start((mockServerPort != -1 ? mockServerPort : null), (mockServerSecurePort != -1 ? mockServerSecurePort : null));
            }
        } else {
            throw new IllegalStateException("MockServer is already running!");
        }

        return abstractRunner;
    }

    public boolean stop(final int serverStopPort, final int proxyStopPort, final int stopWait, final String logLevel) {
        MOCK_SERVER_RUNNER.overrideLogLevel(logLevel);
        boolean serverStopped = serverStopPort == -1 || MOCK_SERVER_RUNNER.stop("127.0.0.1", serverStopPort, stopWait);
        boolean proxyStopped = proxyStopPort == -1 || PROXY_RUNNER.stop("127.0.0.1", proxyStopPort, stopWait);
        return serverStopped && proxyStopped;
    }

    public void stop() {
        if (MOCK_SERVER_RUNNER.isRunning()) {
            MOCK_SERVER_RUNNER.stop();
        }
        if (PROXY_RUNNER.isRunning()) {
            PROXY_RUNNER.stop();
        }
    }
}
