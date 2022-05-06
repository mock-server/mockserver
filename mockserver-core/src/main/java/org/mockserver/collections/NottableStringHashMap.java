package org.mockserver.collections;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.MatchDifference;
import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.KeyAndValue;
import org.mockserver.model.NottableString;

import java.util.*;

import static org.mockserver.collections.ImmutableEntry.entry;
import static org.mockserver.collections.SubSetMatcher.containsSubset;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class NottableStringHashMap {

    private final Map<NottableString, NottableString> backingMap = new LinkedHashMap<>();
    private final RegexStringMatcher regexStringMatcher;

    public NottableStringHashMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, List<? extends KeyAndValue> entries) {
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        for (KeyAndValue keyToMultiValue : entries) {
            put(keyToMultiValue.getName(), keyToMultiValue.getValue());
        }
    }

    @VisibleForTesting
    public NottableStringHashMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, NottableString[]... keyAndValues) {
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        for (NottableString[] keyAndValue : keyAndValues) {
            if (keyAndValue.length >= 2) {
                put(keyAndValue[0], keyAndValue[1]);
            }
        }
    }

    public boolean containsAll(MockServerLogger mockServerLogger, MatchDifference context, NottableStringHashMap subset) {
        return containsSubset(mockServerLogger, context, regexStringMatcher, subset.entryList(), entryList());
    }

    public boolean allKeysNotted() {
        for (NottableString key : backingMap.keySet()) {
            if (!key.isNot()) {
                return false;
            }
        }
        return true;
    }

    public boolean allKeysOptional() {
        for (NottableString key : backingMap.keySet()) {
            if (!key.isOptional()) {
                return false;
            }
        }
        return true;
    }

    public boolean isEmpty() {
        return backingMap.isEmpty();
    }

    private void put(NottableString key, NottableString value) {
        backingMap.put(key, value != null ? value : string(""));
    }

    private List<ImmutableEntry> entryList() {
        if (!backingMap.isEmpty()) {
            List<ImmutableEntry> entrySet = new ArrayList<>();
            for (Map.Entry<NottableString, NottableString> entry : backingMap.entrySet()) {
                entrySet.add(entry(regexStringMatcher, entry.getKey(), entry.getValue()));
            }
            return entrySet;
        } else {
            return Collections.emptyList();
        }
    }
}
