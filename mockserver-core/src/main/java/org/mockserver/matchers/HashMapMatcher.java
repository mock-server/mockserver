package org.mockserver.matchers;

import org.mockserver.collections.CaseInsensitiveRegexHashMap;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.KeysAndValues;

import static org.slf4j.event.Level.DEBUG;

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

    public boolean matches(final MatchDifference context, KeysAndValues values) {
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
                    .setMessageFormat("map subset match failed expected:{}found:{}failed because:{}")
                    .setArguments(keysAndValues, values, "map is not a subset")
            );
            result = false;
        }

        return not != result;
    }

    public boolean isBlank() {
        return matcher == null || matcher.isEmpty();
    }
}
