package org.mockserver.matchers;

import org.mockserver.collections.CaseInsensitiveRegexHashMap;
import org.mockserver.model.KeyAndValue;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class HashMapMatcher extends NotMatcher<List<KeyAndValue>> {
    private final CaseInsensitiveRegexHashMap hashMap;

    public HashMapMatcher(CaseInsensitiveRegexHashMap hashMap) {
        this.hashMap = hashMap;
    }

    public boolean matches(List<KeyAndValue> values) {
        boolean result = false;

        if (hashMap == null) {
            result = true;
        } else if (KeyAndValue.toHashMap(values).containsAll(hashMap)) {
            result = true;
        } else {
            logger.trace("Map [{}] is not a subset of [{}]", this.hashMap, KeyAndValue.toHashMap(values));
        }

        return reverseResultIfNot(result);
    }
}
