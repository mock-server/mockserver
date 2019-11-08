package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.KeysToMultiValues;

import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
public class MultiValueMapMatcher extends NotMatcher<KeysToMultiValues> {
    private static final String[] excludedFields = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final CaseInsensitiveRegexMultiMap multiMap;

    public MultiValueMapMatcher(MockServerLogger mockServerLogger, KeysToMultiValues keysToMultiValues) {
        this.mockServerLogger = mockServerLogger;
        if (keysToMultiValues != null) {
            this.multiMap = keysToMultiValues.toCaseInsensitiveRegexMultiMap(mockServerLogger);
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
        } else if (values.toCaseInsensitiveRegexMultiMap(mockServerLogger).containsAll(multiMap)) {
            result = true;
        } else {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.TRACE)
                    .setLogLevel(TRACE)
                    .setHttpRequest(context)
                    .setMessageFormat("Map [{}] is not a subset of {}")
                    .setArguments(multiMap, values)
            );
            result = false;
        }

        return not != result;
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
