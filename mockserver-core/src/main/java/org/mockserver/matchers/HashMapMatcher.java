package org.mockserver.matchers;

import org.mockserver.collections.CaseInsensitiveRegexHashMap;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.model.KeyAndValue;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class HashMapMatcher extends ObjectWithReflectiveEqualsHashCodeToString implements Matcher<List<KeyAndValue>> {
    private final CaseInsensitiveRegexHashMap multiMap;

    public HashMapMatcher(CaseInsensitiveRegexHashMap multiMap) {
        this.multiMap = multiMap;
    }

    public boolean matches(List<KeyAndValue> values) {
        boolean result = false;

        if (KeyAndValue.toHashMap(values).containsAll(multiMap)) {
            result = true;
        } else {
            logger.trace("Map [{}] is not a subset of [{}]", this.multiMap, KeyAndValue.toHashMap(values));
        }

        return result;
    }
}
