package org.mockserver.maven;

import org.apache.maven.plugins.annotations.Mojo;
import org.mockserver.client.initialize.ExpectationInitializer;
import org.mockserver.configuration.ConfigurationProperties;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Run the MockServer and wait for a specified timeout (or indefinitely)
 *
 * @author jamesdbloom
 */
@SuppressWarnings("deprecation")
@Mojo(name = "run", requiresProject = false)
public class MockServerRunAndWaitMojo extends MockServerAbstractMojo {

    // used to simplify waiting logic
    @SuppressWarnings("FieldMayBeFinal")
    private CompletableFuture<Object> settableFuture = new CompletableFuture<>();

    public void execute() {
        if (isNotBlank(logLevel)) {
            ConfigurationProperties.logLevel(logLevel);
        }
        if (skip) {
            getLog().info("Skipping plugin execution");
        } else {
            if (getLog().isInfoEnabled()) {
                getLog().info("mockserver:run about to start MockServer on: "
                        + (getServerPorts() != null ? " serverPort " + Arrays.toString(getServerPorts()) : "")
                );
            }
            try {
                ExpectationInitializer initializerClass = createInitializerClass();
                String initializerJson = createInitializerJson();
                if (timeout != null && timeout > 0) {
                    getLocalMockServerInstance().start(getServerPorts(), proxyRemotePort, proxyRemoteHost, logLevel, initializerClass, initializerJson);
                    try {
                        settableFuture.get(timeout, TimeUnit.SECONDS);
                    } catch (TimeoutException te) {
                        // do nothing this is an expected exception when the timeout expires
                    }
                } else {
                    getLocalMockServerInstance().start(getServerPorts(), proxyRemotePort, proxyRemoteHost, logLevel, initializerClass, initializerJson);
                    settableFuture.get();
                }
            } catch (Exception e) {
                getLog().error("Exception while running MockServer", e);
            }
        }

    }

}
