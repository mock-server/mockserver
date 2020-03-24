package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.KeysToMultiValues;

import static org.slf4j.event.Level.DEBUG;

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

    public boolean matches(final MatchDifference context, KeysToMultiValues values) {
        boolean result;

        if (matcher == null || matcher.isEmpty()) {
            result = true;
        } else if (values == null || values.isEmpty()) {
            result = matcher.allKeysNotted();
        } else if (values.toCaseInsensitiveRegexMultiMap(mockServerLogger, controlPlaneMatcher).containsAll(matcher)) {
            result = true;
        } else {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setMatchDifference(context)
                    .setMessageFormat("multimap subset match failed expected:{}found:{}failed because:{}")
                    .setArguments(keysToMultiValues, values, "multimap is not a subset")
            );
            result = false;
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
