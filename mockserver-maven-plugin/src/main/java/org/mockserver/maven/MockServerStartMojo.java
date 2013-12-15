package org.mockserver.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Start the MockServer in the initialize phase of the build and continue build so that tests can run that rely on the MockServer
 *
 * @author jamesdbloom
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.INITIALIZE)
@Execute(goal = "start", phase = LifecyclePhase.INITIALIZE, lifecycle = "mockserver_cycle")
public class MockServerStartMojo extends AbstractMojo {


    /**
     * The port to run MockServer on
     */
    @Parameter(property = "mockserver.port", defaultValue = "8080")
    private int port;

    /**
     * Logging level
     */
    @Parameter(property = "mockserver.logLevel", defaultValue = "WARN")
    private String logLevel;

    /**
     * Skip the plugin execution completely
     */
    @Parameter(property = "mockserver.skip", defaultValue = "false")
    private boolean skip;

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            getLog().info("Starting MockServer on port " + port);
            try {
                new EmbeddedJettyHolder().start(port, logLevel);
            } catch (Exception e) {
                getLog().error("Exception while running MockServer", e);
            }
        }

    }
}
