package org.mockserver.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.mockserver.server.MockServerRunner;


/**
 * Stop the a forked instance of the MockServer
 *
 * @author jamesdbloom
 */
@Mojo(name = "stopForked", requiresProject = false, threadSafe = false)
public class MockServerStopForkedMojo extends AbstractMojo {

    /**
     * Logging level
     */
    @Parameter(property = "mockserver.logLevel", defaultValue = "WARN")
    private String logLevel;

    /**
     * The port to stop MockServer
     */
    @Parameter(property = "mockserver.stopPort", defaultValue = "8081")
    private int stopPort;

    /**
     * Key to provide when stopping MockServer
     */
    @Parameter(property = "mockserver.stopKey", defaultValue = "STOP_KEY")
    protected String stopKey;

    /**
     * Max time in seconds to wait for the MockServer to stop
     */
    @Parameter(property = "mockserver.stopWait")
    protected int stopWait;


    public void execute() throws MojoExecutionException {
        getLog().info("Stopping MockServer using stopPort " + stopPort);
        MockServerRunner.overrideLogLevel(logLevel);
        MockServerRunner.stopRemote("127.0.0.1", stopPort, stopKey, stopWait);
    }
}
