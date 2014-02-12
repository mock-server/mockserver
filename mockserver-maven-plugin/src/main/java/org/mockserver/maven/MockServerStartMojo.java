package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.mockserver.logging.Logging;

/**
 * Start the MockServer in the initialize phase of the build and continue build so that tests can run that rely on the MockServer
 *
 * @author jamesdbloom
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.INITIALIZE)
public class MockServerStartMojo extends MockServerAbstractMojo {

    public void execute() throws MojoExecutionException {
        Logging.overrideLogLevel(logLevel);
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            getLog().info("Starting MockServer on"
                    + (serverPort != -1 ? " serverPort " + serverPort : "")
                    + (serverSecurePort != -1 ? " serverSecurePort " + serverSecurePort : "")
                    + (proxyPort != -1 ? " proxyPort " + proxyPort : "")
                    + (proxySecurePort != -1 ? " proxySecurePort " + proxySecurePort : "")
            );
            getEmbeddedJettyHolder().start(serverPort, serverSecurePort, proxyPort, proxySecurePort);
        }

    }
}
