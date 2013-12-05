package org.mockserver.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author jamesdbloom
 */
@Mojo(name = "stop", requiresProject = false, threadSafe = true, aggregator = true)
public class MockServerStopPlugin extends AbstractMojo {

    @Parameter(property = "mockserver.port", defaultValue = "9090")
    private String port;

    /**
     * Skip plugin execution completely.
     */
    @Parameter(property = "mockserver.skip", defaultValue = "false")
    private boolean skip;

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
