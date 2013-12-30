package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.mockserver.server.MockServerRunner;


/**
 * Stop the a forked instance of the MockServer
 *
 * @author jamesdbloom
 */
@Mojo(name = "stopForked", requiresProject = false, threadSafe = false)
public class MockServerStopForkedMojo extends MockServerAbstractMojo {

    public void execute() throws MojoExecutionException {
        getLog().info("Stopping MockServer using stopPort " + stopPort);
        MockServerRunner.overrideLogLevel(logLevel);
        MockServerRunner.stopRemote("127.0.0.1", stopPort, stopKey, stopWait);
    }
}
