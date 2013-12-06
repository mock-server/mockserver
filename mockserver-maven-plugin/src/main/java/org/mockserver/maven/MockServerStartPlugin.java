package org.mockserver.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author jamesdbloom
 */
@Mojo(name = "start",
        defaultPhase = LifecyclePhase.INITIALIZE,
        requiresProject = false,
        threadSafe = false,
        instantiationStrategy = InstantiationStrategy.KEEP_ALIVE)
@Execute(phase = LifecyclePhase.INITIALIZE)
public class MockServerStartPlugin extends AbstractMojo {

    private static final int TIMEOUT = -1;
    private static final int PORT = 9090;

    @Parameter(property = "mockserver.port", defaultValue = "" + PORT)
    private String port = "" + PORT;

    @Parameter(property = "mockserver.timeout", defaultValue = "" + TIMEOUT)
    private String timeout = "" + TIMEOUT;

    @Parameter(property = "mockserver.logLevel", defaultValue = "WARN")
    private String logLevel = "WARN";

    /**
     * Skip plugin execution completely.
     */
    @Parameter(property = "mockserver.skip", defaultValue = "false")
    private boolean skip = false;

    public static void main(String[] args) throws MojoExecutionException {
        MockServerStartPlugin mockServerStartPlugin = new MockServerStartPlugin();
        mockServerStartPlugin.execute();
    }

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            int timeout = TIMEOUT;
            try {
                timeout = Integer.parseInt(this.timeout);
            } catch (NumberFormatException nfe) {
                getLog().error("Timeout specified [" + timeout + "] is not a valid number");
            }
            int port = PORT;
            try {
                port = Integer.parseInt(this.port);
            } catch (NumberFormatException nfe) {
                getLog().error("Port specified [" + port + "] is not a valid number");
            }
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
