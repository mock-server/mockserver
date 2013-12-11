package org.mockserver.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Stop the MockServer in the verify phase of the build after any integration tests have completed
 *
 * @author jamesdbloom
 */
@Mojo(name = "stop", defaultPhase = LifecyclePhase.VERIFY)
@Execute(goal = "stop", phase = LifecyclePhase.VERIFY, lifecycle = "mockserver_cycle")
public class MockServerStopMojo extends AbstractMojo {

    /**
     * Skip plugin execution completely.
     */
    @Parameter(property = "mockserver.skip", defaultValue = "false")
    private boolean skip;

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            getLog().info("Stopping the MockServer");
            try {
                new EmbeddedJettyHolder().stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
