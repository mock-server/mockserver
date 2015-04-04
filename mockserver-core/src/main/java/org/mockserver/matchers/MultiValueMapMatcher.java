package org.mockserver.matchers;

import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.model.KeyToMultiValue;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class MultiValueMapMatcher extends NotMatcher<List<KeyToMultiValue>> {
    private final CaseInsensitiveRegexMultiMap multiMap;

    public MultiValueMapMatcher(CaseInsensitiveRegexMultiMap multiMap) {
        this.multiMap = multiMap;
    }

    public boolean matches(List<KeyToMultiValue> values) {
        boolean result = false;

        if (multiMap == null) {
            result = true;
        } else if (KeyToMultiValue.toMultiMap(values).containsAll(multiMap)) {
            result = true;
        } else {
            logger.trace("Map [{}] is not a subset of [{}]", multiMap, KeyToMultiValue.toMultiMap(values));
        }

        return reverseResultIfNot(result);
    }
}
