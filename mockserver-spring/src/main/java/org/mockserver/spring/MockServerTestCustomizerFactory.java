package org.mockserver.spring;

import java.util.List;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;

public class MockServerTestCustomizerFactory implements ContextCustomizerFactory {
    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        final MockServerTest mockServerTest = AnnotatedElementUtils.findMergedAnnotation(testClass, MockServerTest.class);
        if (mockServerTest != null) {
            return new MockServerPropertyCustomizer(mockServerTest.value());
        }
        return null;
    }
}
