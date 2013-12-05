package org.mockserver.maven;

import org.mockserver.server.EmbeddedJettyRunner;

/**
 * @author jamesdbloom
 */
public class EmbeddedJettyHolder {

    private static EmbeddedJettyRunner embeddedJettyRunner;

    public void start(int port, String logLevel) {
        EmbeddedJettyRunner.overrideLogLevel(logLevel);
        embeddedJettyRunner = new EmbeddedJettyRunner(port);
    }

    public void stop() throws Exception {
        embeddedJettyRunner.stop();
    }
}
