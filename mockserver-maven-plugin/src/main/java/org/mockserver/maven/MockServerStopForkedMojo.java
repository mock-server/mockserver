package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.mockserver.logging.Logging;


/**
 * Stop the a forked instance of the MockServer
 *
 * @author jamesdbloom
 */
@Mojo(name = "stopForked", requiresProject = false, threadSafe = false)
public class MockServerStopForkedMojo extends MockServerAbstractMojo {

    public void execute() throws MojoExecutionException {
        Logging.overrideLogLevel(logLevel);
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            getEmbeddedJettyHolder().stop(serverPort, proxyPort);
            if (serverPort != -1) {
                getLog().info("Stopped MockServer running on port [" + serverPort + "]");
            }
            if (proxyPort != -1) {
                getLog().info("Stopped the proxy running on port [" + proxyPort + "]");
            }
            if (proxySecurePort != -1) {
                getLog().info("Stopped the secure proxy running on port [" + proxySecurePort + "]");
            }
        }
    }
}
