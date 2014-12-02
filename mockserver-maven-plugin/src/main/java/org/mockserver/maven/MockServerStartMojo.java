package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.mockserver.logging.Logging;

/**
 * Start the MockServer in the initialize phase of the build and continue build so that tests can run that rely on the MockServer
 *
 * @author jamesdbloom
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.INITIALIZE, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresProject = true, inheritByDefault = true)
public class MockServerStartMojo extends MockServerAbstractMojo {

    /**
     * Holds reference to jetty across plugin execution
     */
    private InstanceHolder embeddedJettyHolder;

    public void execute() throws MojoExecutionException {
        Logging.overrideLogLevel(logLevel);
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            if (getLog().isInfoEnabled()) {
                getLog().info("mockserver:start about to start MockServer on: "
                        + (serverPort != -1 ? " serverPort " + serverPort : "")
                        + (serverSecurePort != -1 ? " serverSecurePort " + serverSecurePort : "")
                        + (proxyPort != -1 ? " proxyPort " + proxyPort : "")
                );
            }
            getEmbeddedJettyHolder().start(serverPort, serverSecurePort, proxyPort, createInitializer());
        }

    }
}
