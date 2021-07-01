package org.mockserver.springtest;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockPropertySource;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.util.SocketUtils;

import java.util.List;
import java.util.regex.Pattern;

public class MockServerPropertyCustomizer implements ContextCustomizer {
    private static final int mockServerPort = SocketUtils.findAvailableTcpPort();

    private static final Pattern MOCK_SERVER_PORT_PATTERN = Pattern.compile("\\$\\{mockServerPort}");

    private final List<String> properties;

    MockServerPropertyCustomizer(List<String> properties) {
        this.properties = properties;
    }

    @Override
    public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
        context
            .getEnvironment()
            .getPropertySources()
            .addLast(new MockPropertySource().withProperty("mockServerPort", mockServerPort));

        properties.forEach(property -> {
                String replacement =
                    MOCK_SERVER_PORT_PATTERN.matcher(property).replaceAll(String.valueOf(mockServerPort));

                TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, replacement);
            }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MockServerPropertyCustomizer that = (MockServerPropertyCustomizer) o;
        return Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties);
    }
}
