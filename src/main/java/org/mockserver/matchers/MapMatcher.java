package org.mockserver.matchers;

import com.google.common.collect.Multimap;
import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.ModelObject;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class MapMatcher extends ModelObject implements Matcher<List<KeyToMultiValue>> {
    private final Multimap<String, String> multimap;

    public MapMatcher(Multimap<String, String> multimap) {
        this.multimap = multimap;
    }

    public boolean matches(List<KeyToMultiValue> values) {
        boolean result = false;

        if (containsAll(KeyToMultiValue.toMultiMap(values), this.multimap)) {
            result = true;
        } else {
            logger.trace("Failed to match {} with {}", values, multimap);
        }

        return result;
    }

    private boolean containsAll(Multimap<String, String> superset, Multimap<String, String> subset) {
        for (String key : subset.keySet()) {
            for (String value : subset.get(key)) {
                boolean regexMatches = false;
                if (!superset.containsKey(key)) {
                    return false;
                } else { // key does exist
                    for (String supersetValue : superset.get(key)) {
                        if (supersetValue.matches(value)) {
                            regexMatches = true;
                        }
                    }
                    if (!regexMatches) {
                        return false;
                    }
                }
                if (!regexMatches && !superset.containsEntry(key, value)) {
                    return false;
                }
            }
        }
        return true;
    }
}
