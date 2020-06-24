package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.KeysToMultiValues;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class MultiValueMapMatcher extends NotMatcher<KeysToMultiValues> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final CaseInsensitiveRegexMultiMap matcher;
    private final KeysToMultiValues keysToMultiValues;
    private final boolean controlPlaneMatcher;

    MultiValueMapMatcher(MockServerLogger mockServerLogger, KeysToMultiValues keysToMultiValues, boolean controlPlaneMatcher) {
        this.mockServerLogger = mockServerLogger;
        this.keysToMultiValues = keysToMultiValues;
        this.controlPlaneMatcher = controlPlaneMatcher;
        if (keysToMultiValues != null) {
            this.matcher = keysToMultiValues.toCaseInsensitiveRegexMultiMap(mockServerLogger, controlPlaneMatcher);
        } else {
            this.matcher = null;
        }
    }

    public boolean matches(final MatchDifference context, KeysToMultiValues matched) {
        boolean result;

        if (matcher == null || matcher.isEmpty()) {
            result = true;
        } else if (matched == null || matched.isEmpty()) {
            result = matcher.allKeysNotted();
        } else {
            result = matched.toCaseInsensitiveRegexMultiMap(mockServerLogger, controlPlaneMatcher).containsAll(matcher);
        }

        if (!result && context != null) {
            context.addDifference(mockServerLogger, "multimap subset match failed expected:{}found:{}failed because:{}", keysToMultiValues, matched != null ? matched : "none", matched != null ? "multimap is not a subset" : "none is not a subset");
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
