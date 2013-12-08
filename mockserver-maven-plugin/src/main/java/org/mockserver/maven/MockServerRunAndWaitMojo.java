package org.mockserver.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Run the Mock Server and wait for a specified timeout (or indefinitely)
 *
 * @author jamesdbloom
 */
@Mojo(name = "run", requiresProject = false, threadSafe = false)
public class MockServerRunAndWaitMojo extends AbstractMojo {

    /**
     * The port to run Mock Server on
     */
    @Parameter(property = "mockserver.port", defaultValue = "8080")
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
                if (timeout > 0) {
                    new EmbeddedJettyHolder().start(port, logLevel).get(timeout, TimeUnit.SECONDS);
                } else {
                    new EmbeddedJettyHolder().start(port, logLevel).get();
                }
            } catch (TimeoutException te) {
                getLog().info(timeout + "s timeout ended Mock Server will terminate");
            } catch (Exception e) {
                getLog().error("Exception while running Mock Server", e);
            }
        }

    }
}
