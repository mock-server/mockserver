package org.mockserver.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author jamesdbloom
 */
public abstract class MockServerAbstractMojo extends AbstractMojo {

    /**
     * The port to run MockServer on
     */
    @Parameter(property = "mockserver.port", defaultValue = "-1")
    protected int port;
    /**
     * The secure port to run MockServer on
     */
    @Parameter(property = "mockserver.securePort", defaultValue = "-1")
    protected int securePort;
    /**
     * Timeout to wait before stopping MockServer, to run MockServer indefinitely do not set a value
     */
    @Parameter(property = "mockserver.timeout")
    protected int timeout;
    /**
     * Logging level
     */
    @Parameter(property = "mockserver.logLevel", defaultValue = "WARN")
    protected String logLevel;
    /**
     * Skip the plugin execution completely
     */
    @Parameter(property = "mockserver.skip", defaultValue = "false")
    protected boolean skip;
    /**
     * Logging level
     */
    @Parameter(property = "mockserver.pipeLogToConsole", defaultValue = "false")
    protected boolean pipeLogToConsole;
    /**
     * The port to stop MockServer
     */
    @Parameter(property = "mockserver.stopPort", defaultValue = "8081")
    protected int stopPort;
    /**
     * Max time in seconds to wait for the MockServer to stop
     */
    @Parameter(property = "mockserver.stopWait")
    protected int stopWait;

    /**
     * Holds reference to jetty across plugin execution
     */
    private EmbeddedJettyHolder embeddedJettyHolder;

    protected EmbeddedJettyHolder getEmbeddedJettyHolder() {
        if (embeddedJettyHolder == null) {
            // create on demand to avoid log creation for skipped plugins
            embeddedJettyHolder = new EmbeddedJettyHolder();
        }
        return embeddedJettyHolder;
    }
}
