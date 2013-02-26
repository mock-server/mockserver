package org.jamesdbloom.mockserver.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class KeyToMultiValue<K, V> extends ModelObject {
    private final K name;
    private final List<V> values;

    public KeyToMultiValue(K name, V... values) {
        this.name = name;
        if (values != null) {
            this.values = Arrays.asList(values);
        } else {
            this.values = null;
        }
    }

    public static <K, V> Multimap<K, V> toMultiMap(List<? extends KeyToMultiValue<K, V>> keyToMultiValues) {
        Multimap<K, V> headersMap = HashMultimap.create();
        for (KeyToMultiValue<K, V> keyToMultiValue : keyToMultiValues) {
            for (V value : keyToMultiValue.getValues()) {
                headersMap.put(keyToMultiValue.getName(), value);
            }
        }
        return headersMap;
    }

    public static <K, V> Multimap<K, V> toMultiMap(KeyToMultiValue<K, V>... keyToMultiValues) {
        return toMultiMap(Arrays.asList(keyToMultiValues));
    }

    public K getName() {
        return name;
    }

    public List<V> getValues() {
        return values;
    }
}
