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
    @Parameter(property = "mockserver.serverPort", defaultValue = "-1")
    protected int serverPort = -1;
    /**
     * The secure port to run MockServer on
     */
    @Parameter(property = "mockserver.serverSecurePort", defaultValue = "-1")
    protected int serverSecurePort = -1;
    /**
     * The port to run the proxy on
     */
    @Parameter(property = "mockserver.proxyPort", defaultValue = "-1")
    protected int proxyPort = -1;
    /**
     * The secure port to run the proxy on
     */
    @Parameter(property = "mockserver.proxySecurePort", defaultValue = "-1")
    protected int proxySecurePort = -1;
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
    @Parameter(property = "mockserver.serverStopPort", defaultValue = "8081")
    protected int serverStopPort;
    /**
     * The port to stop the proxy
     */
    @Parameter(property = "mockserver.proxyStopPort", defaultValue = "8081")
    protected int proxyStopPort;
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
