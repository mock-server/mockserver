package org.mockserver.collections;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class CircularHashMap<K, V> extends LinkedHashMap<K, V> {
    static final long serialVersionUID = 1530623482381786485L;
    private final int maxSize;

    public CircularHashMap(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
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
