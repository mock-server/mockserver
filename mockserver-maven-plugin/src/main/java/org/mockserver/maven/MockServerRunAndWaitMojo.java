package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.concurrent.TimeUnit;

/**
 * Run the MockServer and wait for a specified timeout (or indefinitely)
 *
 * @author jamesdbloom
 */
@Mojo(name = "run", requiresProject = false, threadSafe = false)
public class MockServerRunAndWaitMojo extends MockServerAbstractMojo {

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            getLog().info("Starting MockServer on port " + serverPort);
            try {
                if (timeout > 0) {
                    getEmbeddedJettyHolder().start(serverPort, serverSecurePort, proxyPort, proxySecurePort, logLevel).join(TimeUnit.SECONDS.toMillis(timeout));
                } else {
                    getEmbeddedJettyHolder().start(serverPort, serverSecurePort, proxyPort, proxySecurePort, logLevel).join();
                }
            } catch (Exception e) {
                getLog().error("Exception while running MockServer", e);
            }
        }

    }
}
