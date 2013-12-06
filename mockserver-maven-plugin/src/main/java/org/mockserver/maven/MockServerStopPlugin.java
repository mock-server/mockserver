package org.mockserver.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author jamesdbloom
 */
@Mojo(name = "stop",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresProject = false,
        threadSafe = true)
public class MockServerStopPlugin extends AbstractMojo {

    private static final int PORT = 9090;

    @Parameter(property = "mockserver.port", defaultValue = "" + PORT)
    private String port = "" + PORT;

    /**
     * Skip plugin execution completely.
     */
    @Parameter(property = "mockserver.skip", defaultValue = "false")
    private boolean skip = false;

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            getLog().info("execute - Stopping on port " + port);
            try {
                new EmbeddedJettyHolder().stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
