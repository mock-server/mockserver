package org.mockserver.matchers;

import org.mockserver.collections.CaseInsensitiveRegexHashMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.KeyAndValue;
import org.mockserver.model.KeysAndValues;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HashMapMatcher extends NotMatcher<List<KeyAndValue>> {

    private final MockServerLogger mockServerLogger;
    private final CaseInsensitiveRegexHashMap hashMap;

    public HashMapMatcher(MockServerLogger mockServerLogger, CaseInsensitiveRegexHashMap hashMap) {
        this.mockServerLogger = mockServerLogger;
        this.hashMap = hashMap;
    }

    public boolean matches(KeyAndValue... values) {
        return matches(null, Arrays.asList(values));
    }

    public boolean matches(HttpRequest context, List<KeyAndValue> values) {
        boolean result = false;

        if (hashMap == null || hashMap.isEmpty()) {
            result = true;
        } else if (KeysAndValues.toCaseInsensitiveRegexMultiMap(values).containsAll(hashMap)) {
            result = true;
        } else {
            mockServerLogger.trace(context, "Map [{}] is not a subset of {}", this.hashMap, values);
        }

        return reverseResultIfNot(result);
    }
}
