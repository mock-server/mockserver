package org.mockserver.springtest;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;

public class MockServerTestCustomizerFactory implements ContextCustomizerFactory {
  @Override
  public ContextCustomizer createContextCustomizer(
      Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
    final Set<MockServerTest> mockServerTestAnnotations =
        AnnotatedElementUtils.findAllMergedAnnotations(testClass, MockServerTest.class);
    final List<String> properties =
        mockServerTestAnnotations.stream()
            .map(MockServerTest::value)
            .flatMap(Arrays::stream)
            .collect(Collectors.toList());

    return new MockServerPropertyCustomizer(properties);
  }
}
