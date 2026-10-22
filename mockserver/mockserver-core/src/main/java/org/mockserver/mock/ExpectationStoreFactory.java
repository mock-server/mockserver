package org.mockserver.mock;

import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.extension.ExtensionRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.scheduler.Scheduler;

/**
 * Pluggable factory for the expectation store ({@link RequestMatchers}).
 * <p>
 * G10 phase 1 — clustered-state seam. {@link HttpState} obtains its
 * {@link RequestMatchers} through this factory rather than constructing it
 * directly, so an optional clustered backend (phase 2, an embedded Infinispan
 * data grid sharing expectations across MockServer instances) can register a
 * {@link Factory} that returns a clustering-aware {@code RequestMatchers}
 * subclass without {@code mockserver-core} depending on the data grid.
 * <p>
 * <b>Zero behaviour change in phase 1:</b> the default factory is the standard
 * {@link RequestMatchers} constructor, so when no override is registered the
 * behaviour is identical to constructing it inline.
 * <p>
 * Thread-safety: the factory reference is {@code volatile}; register/reset are
 * expected at startup (mirrors the SPI-registry pattern used by
 * {@code AsyncApiControlPlaneRegistry}, {@code DriftStore}, {@code WasmStore}).
 * <p>
 * <b>Phase 2 (deferred):</b> a clustered backend additionally requires the
 * expectation storage (the {@code CircularPriorityQueue} inside
 * {@code RequestMatchers}) to be overridable so a distributed map can back it;
 * that storage abstraction is out of scope for this construction-seam phase.
 */
public final class ExpectationStoreFactory {

    /**
     * Creates the {@link RequestMatchers} instance backing an {@link HttpState}.
     * The parameter order matches the {@link RequestMatchers} constructor so the
     * default can be a constructor reference.
     */
    @FunctionalInterface
    public interface Factory {
        RequestMatchers create(Configuration configuration,
                               MockServerLogger mockServerLogger,
                               Scheduler scheduler,
                               WebSocketClientRegistry webSocketClientRegistry);

        /**
         * Creates the store for a server that may have extensions loaded. The default ignores the registry, so a
         * factory written against the four-argument form keeps working unchanged.
         *
         * @param extensionRegistry the server's extensions, or {@code null} when none are configured
         */
        default RequestMatchers create(Configuration configuration,
                                       MockServerLogger mockServerLogger,
                                       Scheduler scheduler,
                                       WebSocketClientRegistry webSocketClientRegistry,
                                       ExtensionRegistry extensionRegistry) {
            return create(configuration, mockServerLogger, scheduler, webSocketClientRegistry);
        }
    }

    private static final Factory DEFAULT_FACTORY = RequestMatchers::new;

    private static volatile Factory factory = DEFAULT_FACTORY;

    private ExpectationStoreFactory() {
    }

    /**
     * Register a custom factory (e.g. a clustered backend). Passing {@code null}
     * resets to the default in-memory factory.
     */
    public static void register(Factory customFactory) {
        factory = (customFactory != null) ? customFactory : DEFAULT_FACTORY;
    }

    /**
     * Reset to the default in-memory factory (primarily for tests).
     */
    public static void resetToDefault() {
        factory = DEFAULT_FACTORY;
    }

    /**
     * @return {@code true} if a non-default factory is registered.
     */
    public static boolean isCustomFactoryRegistered() {
        return factory != DEFAULT_FACTORY;
    }

    /**
     * Create the expectation store via the registered factory (default: the
     * standard in-memory {@link RequestMatchers}).
     */
    public static RequestMatchers create(Configuration configuration,
                                         MockServerLogger mockServerLogger,
                                         Scheduler scheduler,
                                         WebSocketClientRegistry webSocketClientRegistry) {
        return factory.create(configuration, mockServerLogger, scheduler, webSocketClientRegistry);
    }

    /**
     * Create the expectation store via the registered factory, passing the server's extensions.
     *
     * @param extensionRegistry the server's extensions, or {@code null} when none are configured
     */
    public static RequestMatchers create(Configuration configuration,
                                         MockServerLogger mockServerLogger,
                                         Scheduler scheduler,
                                         WebSocketClientRegistry webSocketClientRegistry,
                                         ExtensionRegistry extensionRegistry) {
        return factory.create(configuration, mockServerLogger, scheduler, webSocketClientRegistry, extensionRegistry);
    }
}
