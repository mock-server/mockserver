package org.mockserver.client.initialize;

import org.mockserver.client.MockServerClient;

/**
 * If the MockServer is started using the Maven Plugin a initializationClass property can be specified to initialize expectations, when the MockServer starts.
 *
 * Note: the plugin must be started during the process-test-classes to ensure that the initialization class has been compiled from either src/main/java or
 * src/test/java locations. In addition the initializer can only be used with start and run goals, it will not work with the runForked goal as a JVM is forked
 * with a separate classpath. (required: false, default: false)
 *
 * See: http://mock-server.com/mock_server/initializing_expectations.html#maven_plugin_expectation_initializer_class
 *
 * @author jamesdbloom
 */
public interface PluginExpectationInitializer extends ExpectationInitializer {

    void initializeExpectations(MockServerClient mockServerClient);

}
