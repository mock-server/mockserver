package org.mockserver.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.concurrent.TimeUnit;

/**
 * @author jamesdbloom
 */
@Mojo(name = "start", requiresProject = false, threadSafe = true, aggregator = true)
public class MockServerStartPlugin extends AbstractMojo {

    @Parameter(property = "mockserver.port", defaultValue = "9090")
    private String port;

    @Parameter(property = "mockserver.logLevel", defaultValue = "WARN")
    private String logLevel;

    /**
     * Skip plugin execution completely.
     */
    @Parameter(property = "mockserver.skip", defaultValue = "false")
    private boolean skip;

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            getLog().info("execute - Starting on port " + port);
            try {
                new EmbeddedJettyHolder().start(Integer.parseInt(port), logLevel);
            } catch (NumberFormatException nfe) {
                getLog().error("Port specified [" + port + "] is not a valid number");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
