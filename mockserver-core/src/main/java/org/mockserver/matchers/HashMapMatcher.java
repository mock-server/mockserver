package org.mockserver.matchers;

import org.mockserver.collections.CaseInsensitiveRegexHashMap;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.KeysAndValues;

import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class HashMapMatcher extends NotMatcher<KeysAndValues> {

    private final MockServerLogger mockServerLogger;
    private final boolean controlPlaneMatcher;
    private final CaseInsensitiveRegexHashMap hashMap;

    HashMapMatcher(MockServerLogger mockServerLogger, KeysAndValues keysAndValues, boolean controlPlaneMatcher) {
        this.mockServerLogger = mockServerLogger;
        this.controlPlaneMatcher = controlPlaneMatcher;
        if (keysAndValues != null) {
            this.hashMap = keysAndValues.toCaseInsensitiveRegexMultiMap(mockServerLogger, controlPlaneMatcher);
        } else {
            this.hashMap = null;
        }
    }

    public boolean matches(final HttpRequest context, KeysAndValues values) {
        boolean result;

        if (hashMap == null || hashMap.isEmpty()) {
            result = true;
        } else if (values == null || values.isEmpty()) {
            result = hashMap.allKeysNotted();
        } else if (values.toCaseInsensitiveRegexMultiMap(mockServerLogger, controlPlaneMatcher).containsAll(hashMap)) {
            result = true;
        } else {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.DEBUG)
                    .setLogLevel(DEBUG)
                    .setHttpRequest(context)
                    .setMessageFormat("Map {} is not a subset of {}")
                    .setArguments(this.hashMap, values)
            );
            result = false;
        }

        return not != result;
    }
}
