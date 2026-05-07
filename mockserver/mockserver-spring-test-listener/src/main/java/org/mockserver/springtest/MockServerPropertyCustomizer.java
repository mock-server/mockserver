package org.mockserver.springtest;

import org.mockserver.integration.ClientAndServer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockPropertySource;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class MockServerPropertyCustomizer implements ContextCustomizer {
    private static final Pattern MOCK_SERVER_PORT_PATTERN = Pattern.compile("\\$\\{mockServerPort}");

    private static volatile ClientAndServer clientAndServer;

    private final List<String> properties;

    MockServerPropertyCustomizer(List<String> properties) {
        this.properties = properties;
    }

    static synchronized ClientAndServer getOrCreateClientAndServer() {
        if (clientAndServer == null) {
            clientAndServer = ClientAndServer.startClientAndServer(0);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> clientAndServer.stop()));
        }
        return clientAndServer;
    }

    @Override
    public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
        ClientAndServer server = getOrCreateClientAndServer();
        int port = server.getPort();

        context
            .getEnvironment()
            .getPropertySources()
            .addLast(new MockPropertySource().withProperty("mockServerPort", port));

        properties.forEach(property -> {
                String replacement =
                    MOCK_SERVER_PORT_PATTERN.matcher(property).replaceAll(String.valueOf(port));
                TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, replacement);
            }
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        MockServerPropertyCustomizer that = (MockServerPropertyCustomizer) other;
        return Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties);
    }
}
