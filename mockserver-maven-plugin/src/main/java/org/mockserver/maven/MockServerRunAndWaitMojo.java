package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.mockserver.configuration.ConfigurationProperties;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Run the MockServer and wait for a specified timeout (or indefinitely)
 *
 * @author jamesdbloom
 */
@Mojo(name = "run", requiresProject = false, threadSafe = false)
public class MockServerRunAndWaitMojo extends MockServerAbstractMojo {

    // used to simplify waiting logic
    private SettableFuture settableFuture = SettableFuture.create();

    public void execute() throws MojoExecutionException {
        ConfigurationProperties.overrideLogLevel(logLevel);
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            if (getLog().isInfoEnabled()) {
                getLog().info("mockserver:runAndWait about to start MockServer on: "
                        + (getServerPorts() != null ? " serverPort " + Arrays.toString(getServerPorts()) : "")
                        + (proxyPort != -1 ? " proxyPort " + proxyPort : "")
                );
            }
            try {
                if (timeout > 0) {
                    getEmbeddedJettyHolder().start(getServerPorts(), proxyPort, createInitializer());
                    try {
                        settableFuture.get(timeout, TimeUnit.SECONDS);
                    } catch (TimeoutException te) {
                        // do nothing this is an expected exception when the timeout expires
                    }
                } else {
                    getEmbeddedJettyHolder().start(getServerPorts(), proxyPort, createInitializer());
                    settableFuture.get();
                }
            } catch (Exception e) {
                getLog().error("Exception while running MockServer", e);
            }
        }

    }

}
