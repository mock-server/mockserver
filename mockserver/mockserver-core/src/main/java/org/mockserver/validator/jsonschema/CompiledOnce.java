package org.mockserver.validator.jsonschema;

import java.util.function.Supplier;

/**
 * A value created at most once per JVM, on first use, however many threads ask for it at the same time.
 */
final class CompiledOnce<T> {

    private final Supplier<T> compile;
    private volatile T value;

    CompiledOnce(Supplier<T> compile) {
        this.compile = compile;
    }

    T get() {
        T shared = value;
        if (shared == null) {
            synchronized (this) {
                shared = value;
                if (shared == null) {
                    shared = compile.get();
                    value = shared;
                }
            }
        }
        return shared;
    }

    /**
     * Forgets the value so the next {@link #get()} creates it again; for tests that race the first call.
     */
    synchronized void reset() {
        value = null;
    }
}
