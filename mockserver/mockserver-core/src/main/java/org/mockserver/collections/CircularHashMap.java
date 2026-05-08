package org.mockserver.collections;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author jamesdbloom
 */
public class CircularHashMap<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;
    private final Consumer<V> evictionListener;

    public CircularHashMap(int maxSize) {
        this(maxSize, null);
    }

    public CircularHashMap(int maxSize, Consumer<V> evictionListener) {
        this.maxSize = maxSize;
        this.evictionListener = evictionListener;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        boolean shouldRemove = size() > maxSize;
        if (shouldRemove && evictionListener != null) {
            evictionListener.accept(eldest.getValue());
        }
        return shouldRemove;
    }

    public K findKey(V value) {
        for (Map.Entry<K, V> entry : entrySet()) {
            V entryValue = entry.getValue();
            if (entryValue == value || (value != null && value.equals(entryValue))) {
                return entry.getKey();
            }
        }
        return null;
    }
}
