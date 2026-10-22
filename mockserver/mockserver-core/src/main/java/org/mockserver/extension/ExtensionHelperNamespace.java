package org.mockserver.extension;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A read-only level of the {@code ext} template namespace. Looking up a name that is not there throws, rather than
 * rendering nothing or the reference as written, so a mistyped or unavailable helper fails the template. A stub with
 * no entries is bound as {@code ext} in every template that cannot call helpers.
 */
public final class ExtensionHelperNamespace extends AbstractMap<String, Object> {

    private final String path;
    private final Map<String, Object> entries;
    private final String unavailable;

    private ExtensionHelperNamespace(String path, Map<String, Object> entries, String unavailable) {
        this.path = path;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        this.unavailable = unavailable;
    }

    static ExtensionHelperNamespace of(String path, Map<String, Object> entries) {
        return new ExtensionHelperNamespace(path, entries, null);
    }

    /**
     * @param reason why helpers cannot be called; every lookup throws it
     */
    static ExtensionHelperNamespace unavailable(String reason) {
        return new ExtensionHelperNamespace("ext", Map.of(), reason);
    }

    // true for every name, so a template engine always asks get and the lookup fails loudly
    @Override
    public boolean containsKey(Object key) {
        return key instanceof String;
    }

    @Override
    public Object get(Object key) {
        if (unavailable != null) {
            throw new ExtensionHelperException(unavailable);
        }
        Object value = entries.get(key);
        if (value == null) {
            throw new ExtensionHelperException("there is no extension template helper " + path + "." + key + "; " + path + " has " + entries.keySet());
        }
        return value;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return entries.entrySet();
    }

    @Override
    public String toString() {
        return path;
    }
}
