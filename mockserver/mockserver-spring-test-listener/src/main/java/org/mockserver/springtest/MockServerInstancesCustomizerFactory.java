package org.mockserver.springtest;

import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;

import java.util.List;

/**
 * Creates a {@link MockServerInstancesCustomizer} for a test class that declares {@link MockServerInstance}.
 */
public class MockServerInstancesCustomizerFactory implements ContextCustomizerFactory {
    @Override
    @SuppressWarnings("NullableProblems")
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        List<NamedMockServers.Definition> definitions = NamedMockServers.definitionsFor(testClass);
        return definitions.isEmpty() ? null : new MockServerInstancesCustomizer(definitions);
    }
}
