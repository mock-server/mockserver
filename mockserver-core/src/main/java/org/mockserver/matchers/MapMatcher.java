package org.mockserver.matchers;

import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.model.EqualsHashCodeToString;
import org.mockserver.model.KeyToMultiValue;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class MapMatcher extends EqualsHashCodeToString implements Matcher<List<KeyToMultiValue>> {
    private final CaseInsensitiveRegexMultiMap multiMap;

    public MapMatcher(CaseInsensitiveRegexMultiMap multiMap) {
        this.multiMap = multiMap;
    }

    public boolean matches(List<KeyToMultiValue> values) {
        boolean result = false;

        if (KeyToMultiValue.toMultiMap(values).containsAll(multiMap)) {
            result = true;
        } else {
            logger.trace("Map [{}] is not a subset of [{}]", this.multiMap, KeyToMultiValue.toMultiMap(values));
        }

        return result;
    }
}
