package org.mockserver.springtest;

import org.mockserver.integration.ClientAndServer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts the {@link MockServerInstance} servers of a test class and puts their ports and base URLs in the
 * context's environment, ahead of other property sources so they override application defaults. The context
 * holds a reference to each server until it closes.
 */
public class MockServerInstancesCustomizer implements ContextCustomizer {

    static final String PROPERTY_SOURCE_NAME = "mockServerInstances";

    private final List<NamedMockServers.Definition> definitions;

    MockServerInstancesCustomizer(List<NamedMockServers.Definition> definitions) {
        this.definitions = List.copyOf(definitions);
    }

    @Override
    public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<NamedMockServers.Definition> acquired = new ArrayList<>();
        try {
            for (NamedMockServers.Definition definition : definitions) {
                ClientAndServer server = NamedMockServers.acquire(definition);
                acquired.add(definition);
                int port = server.getPort();
                String baseUrl = "http://localhost:" + port;
                properties.put(definition.portProperty(), port);
                properties.put(definition.baseUrlProperty(), baseUrl);
                definition.portProperties.forEach(name -> properties.put(name, port));
                definition.baseUrlProperties.forEach(name -> properties.put(name, baseUrl));
            }
        } catch (RuntimeException e) {
            acquired.forEach(NamedMockServers::release);
            throw e;
        }
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        context.addApplicationListener(new ReleaseOnClose(context, acquired));
    }

    private static final class ReleaseOnClose implements ApplicationListener<ContextClosedEvent> {
        private final ConfigurableApplicationContext context;
        private final List<NamedMockServers.Definition> definitions;
        private final AtomicBoolean released = new AtomicBoolean();

        private ReleaseOnClose(ConfigurableApplicationContext context, List<NamedMockServers.Definition> definitions) {
            this.context = context;
            this.definitions = definitions;
        }

        @Override
        public void onApplicationEvent(ContextClosedEvent event) {
            // a child context's close event also reaches its parent's listeners
            if (event.getApplicationContext() == context && released.compareAndSet(false, true)) {
                definitions.forEach(NamedMockServers::release);
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return definitions.equals(((MockServerInstancesCustomizer) other).definitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(definitions);
    }
}
