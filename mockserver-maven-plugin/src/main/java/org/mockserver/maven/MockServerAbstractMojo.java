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
    @Parameter(property = "mockserver.logLevel", defaultValue = "INFO")
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
     * Holds reference to jetty across plugin execution
     */
    private InstanceHolder embeddedJettyHolder;

    protected InstanceHolder getEmbeddedJettyHolder() {
        if (embeddedJettyHolder == null) {
            // create on demand to avoid log creation for skipped plugins
            embeddedJettyHolder = new InstanceHolder();
        }
        return embeddedJettyHolder;
    }
}
