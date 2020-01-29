package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.KeysToMultiValues;

import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class MultiValueMapMatcher extends NotMatcher<KeysToMultiValues> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final CaseInsensitiveRegexMultiMap multiMap;
    private final boolean controlPlaneMatcher;

    MultiValueMapMatcher(MockServerLogger mockServerLogger, KeysToMultiValues keysToMultiValues, boolean controlPlaneMatcher) {
        this.mockServerLogger = mockServerLogger;
        this.controlPlaneMatcher = controlPlaneMatcher;
        if (keysToMultiValues != null) {
            this.multiMap = keysToMultiValues.toCaseInsensitiveRegexMultiMap(mockServerLogger, controlPlaneMatcher);
        } else {
            this.multiMap = null;
        }
    }

    public boolean matches(final HttpRequest context, KeysToMultiValues values) {
        boolean result;

        if (multiMap == null || multiMap.isEmpty()) {
            result = true;
        } else if (values == null || values.isEmpty()) {
            result = multiMap.allKeysNotted();
        } else if (values.toCaseInsensitiveRegexMultiMap(mockServerLogger, controlPlaneMatcher).containsAll(multiMap)) {
            result = true;
        } else {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setHttpRequest(context)
                    .setMessageFormat("multimap{}is not a subset of{}")
                    .setArguments(multiMap, values)
            );
            result = false;
        }

        return not != result;
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
