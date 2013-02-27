package org.jamesdbloom.mockserver.matchers;

import com.google.common.collect.Multimap;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.jamesdbloom.mockserver.model.KeyToMultiValue;
import org.jamesdbloom.mockserver.model.ModelObject;

import java.util.List;

import static org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;

/**
 * @author jamesdbloom
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public class MapMatcher<K, V> extends ModelObject implements Matcher<List<KeyToMultiValue<K, V>>> {
    private final Multimap<K, V> multimap;

    @JsonCreator
    public MapMatcher(@JsonProperty("multimap") Multimap<K, V> multimap) {
        this.multimap = multimap;
    }

    public boolean matches(List<KeyToMultiValue<K, V>> values) {
        boolean result = false;

        if (containsAll(KeyToMultiValue.toMultiMap(values), this.multimap)) {
            result = true;
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
