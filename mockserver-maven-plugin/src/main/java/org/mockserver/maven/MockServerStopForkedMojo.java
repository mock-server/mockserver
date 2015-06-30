package org.mockserver.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.mockserver.configuration.ConfigurationProperties;


/**
 * Stop the a forked instance of the MockServer
 *
 * To run from command line:
 *
 *    mvn -Dmockserver.serverPort="1080" -Dmockserver.proxyPort="1090" org.mock-server:mockserver-maven-plugin:3.9.17:stopForked
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
            getEmbeddedJettyHolder().stop(serverPort, proxyPort);
            if (serverPort != -1) {
                getLog().info("Stopped MockServer running on port [" + serverPort + "]");
            }
            if (proxyPort != -1) {
                getLog().info("Stopped the proxy running on port [" + proxyPort + "]");
            }
        }
    }
}
