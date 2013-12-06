package org.mockserver.maven;

import org.mockserver.server.EmbeddedJettyRunner;

import java.util.concurrent.Future;

/**
 * @author jamesdbloom
 */
public class EmbeddedJettyHolder {

    private static final EmbeddedJettyRunner embeddedJettyRunner = new EmbeddedJettyRunner();

    public Future start(final int port, final String logLevel) {
        if (!embeddedJettyRunner.isRunning()) {
            EmbeddedJettyRunner.overrideLogLevel(logLevel);
            return embeddedJettyRunner.start(port);
        } else {
            throw new IllegalStateException("Server is already running!");
        }
    }

    public void stop() throws Exception {
        if (embeddedJettyRunner.isRunning()) {
            embeddedJettyRunner.stop();
        } else {
            throw new IllegalStateException("Server is not running!");
        }
    }
}
