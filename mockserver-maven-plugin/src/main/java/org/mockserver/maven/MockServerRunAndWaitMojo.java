package org.mockserver.maven;

import com.google.common.annotations.VisibleForTesting;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
                    getEmbeddedJettyHolder().start(serverPort, serverSecurePort, proxyPort, proxySecurePort, logLevel);
                    try {
                        newSettableFuture().get(timeout, TimeUnit.SECONDS);
                    } catch (TimeoutException te) {
                        // do nothing this is an expected exception when the timeout expires
                    }
                } else {
                    getEmbeddedJettyHolder().start(serverPort, serverSecurePort, proxyPort, proxySecurePort, logLevel);
                    newSettableFuture().get();
                }
            } catch (Exception e) {
                getLog().error("Exception while running MockServer", e);
            }
        }

    }

    @VisibleForTesting
    SettableFuture<Object> newSettableFuture() {
        return SettableFuture.create();
    }
}
