package org.mockserver.mock;

import org.junit.After;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.extension.Extension;
import org.mockserver.extension.ExtensionRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.scheduler.Scheduler;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;

public class ExpectationStoreFactoryTest {

    @After
    public void reset() {
        // Always restore the global default so a custom factory cannot leak to other tests.
        ExpectationStoreFactory.resetToDefault();
    }

    private RequestMatchers callCreate() {
        return ExpectationStoreFactory.create(
            configuration(), new MockServerLogger(), mock(Scheduler.class), mock(WebSocketClientRegistry.class));
    }

    @Test
    public void shouldDefaultToInMemoryFactory() {
        assertThat(ExpectationStoreFactory.isCustomFactoryRegistered(), is(false));
        RequestMatchers store = callCreate();
        assertThat(store, is(notNullValue()));
    }

    @Test
    public void shouldUseRegisteredCustomFactory() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        // Custom factory delegates to a real in-memory store, so even if a concurrent
        // HttpState construction picks it up during this test it still gets a working store.
        ExpectationStoreFactory.register((c, l, s, w) -> {
            invoked.set(true);
            return new RequestMatchers(c, l, s, w);
        });

        assertThat(ExpectationStoreFactory.isCustomFactoryRegistered(), is(true));
        RequestMatchers store = callCreate();
        assertThat(invoked.get(), is(true));
        assertThat(store, is(notNullValue()));
    }

    @Test
    public void shouldUseAFourArgumentFactoryForAServerWithExtensions() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        ExpectationStoreFactory.register((c, l, s, w) -> {
            invoked.set(true);
            return new RequestMatchers(c, l, s, w);
        });
        ExtensionRegistry registry = ExtensionRegistry.start(configuration().extensions(NamedExtension::new), new MockServerLogger());
        try {
            RequestMatchers store = ExpectationStoreFactory.create(
                configuration(), new MockServerLogger(), mock(Scheduler.class), mock(WebSocketClientRegistry.class), registry);

            assertThat(invoked.get(), is(true));
            assertThat(store, is(notNullValue()));
        } finally {
            registry.stop();
        }
    }

    @Test
    public void shouldPassTheExtensionRegistryToAFactoryThatTakesIt() {
        AtomicReference<ExtensionRegistry> received = new AtomicReference<>();
        ExpectationStoreFactory.register(new ExpectationStoreFactory.Factory() {
            @Override
            public RequestMatchers create(Configuration c, MockServerLogger l, Scheduler s, WebSocketClientRegistry w) {
                return new RequestMatchers(c, l, s, w);
            }

            @Override
            public RequestMatchers create(Configuration c, MockServerLogger l, Scheduler s, WebSocketClientRegistry w, ExtensionRegistry extensionRegistry) {
                received.set(extensionRegistry);
                return new RequestMatchers(c, l, s, w);
            }
        });
        ExtensionRegistry registry = ExtensionRegistry.start(configuration().extensions(NamedExtension::new), new MockServerLogger());
        try {
            ExpectationStoreFactory.create(configuration(), new MockServerLogger(), mock(Scheduler.class), mock(WebSocketClientRegistry.class), registry);

            assertThat(received.get(), is(sameInstance(registry)));
        } finally {
            registry.stop();
        }
    }

    public static class NamedExtension implements Extension {
        @Override
        public String name() {
            return "factory-test";
        }
    }

    @Test
    public void shouldResetToDefault() {
        ExpectationStoreFactory.register((c, l, s, w) -> new RequestMatchers(c, l, s, w));
        assertThat(ExpectationStoreFactory.isCustomFactoryRegistered(), is(true));

        ExpectationStoreFactory.resetToDefault();
        assertThat(ExpectationStoreFactory.isCustomFactoryRegistered(), is(false));
    }

    @Test
    public void shouldTreatNullRegistrationAsDefault() {
        ExpectationStoreFactory.register((c, l, s, w) -> new RequestMatchers(c, l, s, w));
        ExpectationStoreFactory.register(null);
        assertThat(ExpectationStoreFactory.isCustomFactoryRegistered(), is(false));
    }
}
