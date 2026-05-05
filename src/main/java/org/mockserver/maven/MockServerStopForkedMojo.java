package org.mockserver.maven;

import org.apache.maven.plugins.annotations.Mojo;
import org.mockserver.configuration.ConfigurationProperties;

import java.util.Arrays;


/**
 * Stop a forked instance of the MockServer
 *
 * To run from command line:
 *
 *    mvn -Dmockserver.serverPort="1080" org.mock-server:mockserver-maven-plugin:5.5.4:stopForked
 *
 * @author jamesdbloom
 */
@Mojo(name = "stopForked", requiresProject = false)
public class MockServerStopForkedMojo extends MockServerAbstractMojo {

    public void execute() {
        if (logLevel != null) {
            ConfigurationProperties.logLevel(logLevel);
        }
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            getLocalMockServerInstance().stop(getServerPorts(), false);
            if (getServerPorts() != null) {
                getLog().info("Stopped MockServer running on port [" + Arrays.toString(getServerPorts()) + "]");
            }
        }
    }
}
