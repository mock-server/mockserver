package org.mockserver.spring;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;

public class MockServerPropertyCustomizer implements ContextCustomizer {
    private static final Pattern MOCKSERVERPORT_PORT_PATTERN = Pattern.compile("\\$\\{mockServerPort\\}");

    private List<String> properties;

    MockServerPropertyCustomizer(String... properties) {
        this.properties = Arrays.asList(properties);
    }
    @Override
    public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
        if (!properties.isEmpty()) {
            String mockServerPort = context.getEnvironment().getProperty("mockServerPort");
            properties.forEach(property -> {
                String replacement = MOCKSERVERPORT_PORT_PATTERN.matcher(property).replaceAll(mockServerPort);

                TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, replacement);
            });
        }
    }
}
