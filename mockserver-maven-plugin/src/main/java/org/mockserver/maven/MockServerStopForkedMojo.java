package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.mockserver.configuration.ConfigurationProperties;

import java.util.Arrays;


/**
 * Stop the a forked instance of the MockServer
 *
 * To run from command line:
 *
 *    mvn -Dmockserver.serverPort="1080" -Dmockserver.proxyPort="1090" org.mock-server:mockserver-maven-plugin:3.11:stopForked
 *
 * @author jamesdbloom
 */
@Mojo(name = "stopForked", requiresProject = false, threadSafe = false)
public class MockServerStopForkedMojo extends MockServerAbstractMojo {

    public void execute() throws MojoExecutionException {
        ConfigurationProperties.overrideLogLevel(logLevel);
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            getEmbeddedJettyHolder().stop(getServerPorts(), proxyPort, false);
            if (getServerPorts() != null) {
                getLog().info("Stopped MockServer running on port [" + Arrays.toString(getServerPorts()) + "]");
            }
            if (proxyPort != -1) {
                getLog().info("Stopped the proxy running on port [" + proxyPort + "]");
            }
        }
    }
}
