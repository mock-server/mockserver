package org.mockserver.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.mockserver.server.EmbeddedJettyRunner;


/**
 * Stop the a forked instance of the Mock Server
 *
 * @author jamesdbloom
 */
@Mojo(name = "stopForked", requiresProject = false, threadSafe = false)
public class MockServerStopForkedMojo extends AbstractMojo {

    /**
     * The port to stop Mock Server
     */
    @Parameter(property = "mockserver.stopPort", defaultValue = "8081")
    private int stopPort;

    /**
     * Key to provide when stopping Mock Server
     */
    @Parameter(property = "mockserver.stopKey", defaultValue = "STOP_KEY")
    protected String stopKey;

    /**
     * Max time in seconds to wait for the Mock Server to stop
     */
    @Parameter(property = "mockserver.stopWait")
    protected int stopWait;


    public void execute() throws MojoExecutionException {
        EmbeddedJettyRunner.stopRemote("127.0.0.1", stopPort, stopKey, stopWait);
    }
}
