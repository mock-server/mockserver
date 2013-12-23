package org.mockserver.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Run the MockServer and wait for a specified timeout (or indefinitely)
 *
 * @author jamesdbloom
 */
@Mojo(name = "run", requiresProject = false, threadSafe = false)
public class MockServerRunAndWaitMojo extends AbstractMojo {

    /**
     * The port to run MockServer on
     */
    @Parameter(property = "mockserver.port", defaultValue = "8080")
    private int port;

    /**
     * The secure port to run MockServer on
     */
    @Parameter(property = "mockserver.securePort", defaultValue = "1080")
    private int securePort;

    /**
     * Timeout to wait before stopping MockServer, to run MockServer indefinitely do not set a value
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
            getLog().info("Starting MockServer on port " + port);
            try {
                if (timeout > 0) {
                    new EmbeddedJettyHolder().start(port, securePort, logLevel).get(timeout, TimeUnit.SECONDS);
                } else {
                    new EmbeddedJettyHolder().start(port, securePort, logLevel).get();
                }
            } catch (TimeoutException te) {
                getLog().info(timeout + "s timeout ended MockServer will terminate");
            } catch (Exception e) {
                getLog().error("Exception while running MockServer", e);
            }
        }

    }
}
