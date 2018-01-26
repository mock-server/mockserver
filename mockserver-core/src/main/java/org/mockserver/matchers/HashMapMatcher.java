package org.mockserver.matchers;

import org.mockserver.collections.CaseInsensitiveRegexHashMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.KeysAndValues;

/**
 * @author jamesdbloom
 */
public class HashMapMatcher extends NotMatcher<KeysAndValues> {

    private final MockServerLogger mockServerLogger;
    private final CaseInsensitiveRegexHashMap hashMap;

    public HashMapMatcher(MockServerLogger mockServerLogger, KeysAndValues keysAndValues) {
        this.mockServerLogger = mockServerLogger;
        if (keysAndValues != null) {
            this.hashMap = keysAndValues.toCaseInsensitiveRegexMultiMap();
        } else {
            this.hashMap = null;
        }
    }

    public boolean matches(final HttpRequest context, KeysAndValues values) {
        boolean result = false;

        if (hashMap == null || hashMap.isEmpty() || values == null) {
            result = true;
        } else if (values.toCaseInsensitiveRegexMultiMap().containsAll(hashMap)) {
            result = true;
        } else {
            mockServerLogger.trace(context, "Map [{}] is not a subset of {}", this.hashMap, values);
        }

        return not != result;
    }
}
