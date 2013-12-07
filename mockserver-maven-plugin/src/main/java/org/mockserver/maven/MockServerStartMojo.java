package org.mockserver.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Start the Mock Server in the initialize phase of the build and continue build so that tests can run that rely on the Mock Server
 *
 * @author jamesdbloom
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.INITIALIZE)
public class MockServerStartMojo extends AbstractMojo {


    /**
     * The port to run Mock Server on
     */
    @Parameter(property = "mockserver.port", defaultValue = "9090")
    private int port;

    /**
     * Timeout to wait before stopping Mock Server, to run Mock Server indefinitely do not set a value
     */
    @Parameter(property = "mockserver.timeout")
    private int timeout;

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
            getLog().info("Starting Mock Server on port " + port);
            try {
                new EmbeddedJettyHolder().start(port, logLevel);
            } catch (Exception e) {
                getLog().error("Exception while running Mock Server", e);
            }
        }

    }
}
