package org.mockserver.spring;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockPropertySource;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.util.SocketUtils;

public class MockServerPropertyCustomizer implements ContextCustomizer {
    private static final int mockServerPort = SocketUtils.findAvailableTcpPort();

    private static final Pattern MOCKSERVERPORT_PORT_PATTERN = Pattern.compile("\\$\\{mockServerPort}");

    private final List<String> properties;

    MockServerPropertyCustomizer(String... properties) {
        this.properties = Arrays.asList(properties);
    }
    @Override
    public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
        context.getEnvironment().getPropertySources().addLast(
                new MockPropertySource().withProperty("mockServerPort", mockServerPort)
        );

        if (!properties.isEmpty()) {
            properties.forEach(property -> {
                String replacement = MOCKSERVERPORT_PORT_PATTERN.matcher(property).replaceAll(String.valueOf(mockServerPort));

                TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, replacement);
            });
        }
    }
}
