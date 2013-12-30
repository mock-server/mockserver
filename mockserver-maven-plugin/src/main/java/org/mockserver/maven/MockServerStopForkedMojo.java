package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;


/**
 * Stop the a forked instance of the MockServer
 *
 * @author jamesdbloom
 */
@Mojo(name = "stopForked", requiresProject = false, threadSafe = false)
public class MockServerStopForkedMojo extends MockServerAbstractMojo {

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            if (getEmbeddedJettyHolder().stop(stopPort, stopKey, stopWait, logLevel)) {
                getLog().info("Stopped MockServer using stopPort " + stopPort + " and stopKey " + stopKey);
            } else {
                getLog().info("Failed to stop MockServer");
            }
        }
    }
}
