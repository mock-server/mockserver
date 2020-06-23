package org.mockserver.matchers;

import org.mockserver.collections.CaseInsensitiveRegexHashMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.KeysAndValues;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class HashMapMatcher extends NotMatcher<KeysAndValues> {

    private final MockServerLogger mockServerLogger;
    private final KeysAndValues keysAndValues;
    private final boolean controlPlaneMatcher;
    private final CaseInsensitiveRegexHashMap matcher;

    HashMapMatcher(MockServerLogger mockServerLogger, KeysAndValues keysAndValues, boolean controlPlaneMatcher) {
        this.mockServerLogger = mockServerLogger;
        this.keysAndValues = keysAndValues;
        this.controlPlaneMatcher = controlPlaneMatcher;
        if (keysAndValues != null) {
            this.matcher = keysAndValues.toCaseInsensitiveRegexMultiMap(mockServerLogger, controlPlaneMatcher);
        } else {
            this.matcher = null;
        }
    }

    public boolean matches(final MatchDifference context, KeysAndValues matched) {
        boolean result;

        if (matcher == null || matcher.isEmpty()) {
            result = true;
        } else if (matched == null || matched.isEmpty()) {
            result = matcher.allKeysNotted();
        } else if (matched.toCaseInsensitiveRegexMultiMap(mockServerLogger, controlPlaneMatcher).containsAll(matcher)) {
            result = true;
        } else {
            if (context != null) {
                context.addDifference(mockServerLogger, "map subset match failed expected:{}found:{}failed because:{}", keysAndValues, matched, "map is not a subset");
            }
            result = false;
        }

        return not != result;
    }

    public boolean isBlank() {
        return matcher == null || matcher.isEmpty();
    }
}
