package org.mockserver.matchers;

import com.google.common.collect.Multimap;
import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.ModelObject;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class MapMatcher<K, V> extends ModelObject implements Matcher<List<KeyToMultiValue<K, V>>> {
    private final Multimap<K, V> multimap;

    public MapMatcher(Multimap<K, V> multimap) {
        this.multimap = multimap;
    }

    public boolean matches(List<KeyToMultiValue<K, V>> values) {
        boolean result = false;

        if (containsAll(KeyToMultiValue.toMultiMap(values), this.multimap)) {
            result = true;
        } else {
            logger.trace("Failed to match {} with {}", values, multimap);
        }

        return result;
    }

    private boolean containsAll(Multimap<K, V> superset, Multimap<K, V> subset) {
        for (K key : subset.keySet()) {
            for (V value : subset.get(key)) {
                if (!superset.containsEntry(key, value)) {
                    return false;
                }
            }
        }
        return true;
    }
}
