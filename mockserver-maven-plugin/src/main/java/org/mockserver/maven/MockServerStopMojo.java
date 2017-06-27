package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Stop the MockServer in the verify phase of the build after any integration tests have completed
 *
 * @author jamesdbloom
 */
@Mojo(name = "stop", defaultPhase = LifecyclePhase.VERIFY)
public class MockServerStopMojo extends MockServerAbstractMojo {

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            getLog().info("Stopping the MockServer");
            getEmbeddedJettyHolder().stop();
        }
    }
}
