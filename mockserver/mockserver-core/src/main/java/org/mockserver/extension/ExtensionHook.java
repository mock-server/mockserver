package org.mockserver.extension;

import org.mockserver.server.initialize.ExpectationInitializer;

/**
 * The optional capabilities an {@link Extension} can add by also implementing a hook interface. A hook can be
 * disabled for a loaded extension, with a reason, without unloading the extension (see
 * {@link ExtensionRegistry#disableHook(ExtensionHook, String)}).
 */
public enum ExtensionHook {

    /**
     * Expectations loaded once when the server starts, after the configured initialisers.
     */
    EXPECTATION_INITIALIZER("expectationInitializer", ExpectationInitializer.class),

    /**
     * Control-plane routes under {@code /mockserver/extensions/<name>/}, served on a pool of their own.
     */
    ADMIN_API("adminApi", AdminApiExtension.class);

    private final String label;
    private final Class<?> hookType;

    ExtensionHook(String label, Class<?> hookType) {
        this.label = label;
        this.hookType = hookType;
    }

    public String label() {
        return label;
    }

    public boolean isImplementedBy(Extension extension) {
        return hookType.isInstance(extension);
    }

    @Override
    public String toString() {
        return label;
    }
}
