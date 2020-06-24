package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.KeysAndValues;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class HashMapMatcher extends NotMatcher<KeysAndValues> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final CaseInsensitiveRegexHashMap matcher;
    private final KeysAndValues keysAndValues;
    private final boolean controlPlaneMatcher;

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
        } else {
            result = matched.toCaseInsensitiveRegexMultiMap(mockServerLogger, controlPlaneMatcher).containsAll(matcher);
        }

        if (!result && context != null) {
            context.addDifference(mockServerLogger, "map subset match failed expected:{}found:{}failed because:{}", keysAndValues, matched != null ? matched : "none", matched != null ? "map is not a subset" : "none is not a subset");
        }

        return not != result;
    }

    public boolean isBlank() {
        return matcher == null || matcher.isEmpty();
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
