package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.collections.NottableStringHashMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.KeyAndValue;
import org.mockserver.model.KeysAndValues;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class HashMapMatcher extends NotMatcher<KeysAndValues<? extends KeyAndValue, ? extends KeysAndValues>> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final NottableStringHashMap matcher;
    private final KeysAndValues keysAndValues;
    private final boolean controlPlaneMatcher;
    private Boolean allKeysNotted;
    private Boolean allKeysOptional;

    HashMapMatcher(MockServerLogger mockServerLogger, KeysAndValues<? extends KeyAndValue, ? extends KeysAndValues> keysAndValues, boolean controlPlaneMatcher) {
        this.mockServerLogger = mockServerLogger;
        this.keysAndValues = keysAndValues;
        this.controlPlaneMatcher = controlPlaneMatcher;
        if (keysAndValues != null) {
            this.matcher = new NottableStringHashMap(this.mockServerLogger, this.controlPlaneMatcher, keysAndValues.getEntries());
        } else {
            this.matcher = null;
        }
    }

    public boolean matches(final MatchDifference context, KeysAndValues<? extends KeyAndValue, ? extends KeysAndValues> matched) {
        boolean result;

        if (matcher == null || matcher.isEmpty()) {
            result = true;
        } else if (matched == null || matched.isEmpty()) {
            if (allKeysNotted == null) {
                allKeysNotted = matcher.allKeysNotted();
            }
            if (allKeysOptional == null) {
                allKeysOptional = matcher.allKeysOptional();
            }
            result = allKeysNotted || allKeysOptional;
        } else {
            result = new NottableStringHashMap(mockServerLogger, controlPlaneMatcher, matched.getEntries()).containsAll(matcher);
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
