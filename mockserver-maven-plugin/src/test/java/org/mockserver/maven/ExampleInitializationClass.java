package org.mockserver.maven;

import org.mockserver.client.MockServerClient;
import org.mockserver.client.initialize.PluginExpectationInitializer;

/**
 * @author jamesdbloom
 */
public class ExampleInitializationClass implements PluginExpectationInitializer {

    public static MockServerClient mockServerClient;

    @Override
    public void initializeExpectations(MockServerClient mockServerClient) {
        ExampleInitializationClass.mockServerClient = mockServerClient;
    }
}
