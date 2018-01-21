package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.KeysToMultiValues;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class MultiValueMapMatcher extends NotMatcher<List<KeyToMultiValue>> {
    private static final String[] excludedFields = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final CaseInsensitiveRegexMultiMap multiMap;

    public MultiValueMapMatcher(MockServerLogger mockServerLogger, CaseInsensitiveRegexMultiMap multiMap) {
        this.mockServerLogger = mockServerLogger;
        this.multiMap = multiMap;
    }

    public boolean matches(KeyToMultiValue... values) {
        return matches(null, Arrays.asList(values));
    }

    public boolean matches(HttpRequest context, List<KeyToMultiValue> values) {
        boolean result = false;

        if (multiMap == null || multiMap.isEmpty()) {
            result = true;
        } else if (KeysToMultiValues.toCaseInsensitiveRegexMultiMap(values).containsAll(multiMap)) {
            result = true;
        } else {
            mockServerLogger.trace(context, "Map [{}] is not a subset of {}", multiMap, values);
        }

        return reverseResultIfNot(result);
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
