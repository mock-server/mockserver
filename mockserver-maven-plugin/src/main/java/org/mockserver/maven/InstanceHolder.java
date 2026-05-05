package org.mockserver.maven;

import com.google.common.base.Strings;
import org.mockserver.client.MockServerClient;
import org.mockserver.client.initialize.ExpectationInitializer;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.netty.MockServer;
import org.mockserver.serialization.ExpectationSerializer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("deprecation")
public class InstanceHolder extends ObjectWithReflectiveEqualsHashCodeToString {

    private MockServer mockServer;

    public static void runInitialization(Integer[] mockServerPorts, ExpectationInitializer expectationClassInitializer, String expectationJsonInitializer) {
        if (mockServerPorts != null && mockServerPorts.length > 0) {
            if (expectationClassInitializer != null) {
                expectationClassInitializer
                        .initializeExpectations(
                                new MockServerClient("127.0.0.1", mockServerPorts[0])
                        );
            }
            if (isNotBlank(expectationJsonInitializer)) {
                Expectation[] expectations = new ExpectationSerializer(new MockServerLogger()).deserializeArray(expectationJsonInitializer, false);
                new MockServerClient("127.0.0.1", mockServerPorts[0]).sendExpectation(expectations);
            }
        }
    }

    public void start(final Integer[] mockServerPorts,
                      final Integer proxyRemotePort,
                      String proxyRemoteHost,
                      final String logLevel,
                      ExpectationInitializer expectationClassInitializer,
                      String expectationJsonInitializer) {
        if (mockServer == null || !mockServer.isRunning()) {
            if (logLevel != null) {
                ConfigurationProperties.logLevel(logLevel);
            }
            if (mockServerPorts != null && mockServerPorts.length > 0) {
                if (proxyRemotePort != null && proxyRemotePort != -1) {
                    if (Strings.isNullOrEmpty(proxyRemoteHost)) {
                        proxyRemoteHost = "localhost";
                    }
                    mockServer = new MockServer(proxyRemotePort, proxyRemoteHost, mockServerPorts);
                } else {
                    mockServer = new MockServer(mockServerPorts);
                }
                MockServerAbstractMojo.mockServerPort(mockServer.getLocalPort());
            }
            runInitialization(mockServerPorts, expectationClassInitializer, expectationJsonInitializer);
        } else {
            throw new IllegalStateException("MockServer is already running!");
        }
    }

    public void stop(final Integer[] mockServerPorts, boolean ignoreFailure) {
        if (mockServerPorts != null && mockServerPorts.length > 0) {
            try {
                new MockServerClient("127.0.0.1", mockServerPorts[0]).stop(ignoreFailure).get().hasStopped();
            } catch (Throwable throwable) {
                if (!ignoreFailure) {
                    throw new RuntimeException(throwable.getMessage(), throwable);
                }
            }
        }
    }

    public void stop() {
        if (mockServer != null && mockServer.isRunning()) {
            mockServer.stop();

            try {
                // ensure that shutdown has actually completed and won't
                // cause class loader error if JVM starts unloading classes
                SECONDS.sleep(3);
            } catch (InterruptedException ignore) {
                // ignore
            }
        }
    }
}
