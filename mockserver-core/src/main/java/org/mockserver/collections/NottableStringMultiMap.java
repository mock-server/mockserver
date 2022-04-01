package org.mockserver.collections;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.MatchDifference;
import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.NottableString;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.*;

import static org.mockserver.collections.ImmutableEntry.entry;
import static org.mockserver.collections.SubSetMatcher.containsSubset;

/**
 * @author jamesdbloom
 */
public class NottableStringMultiMap extends ObjectWithReflectiveEqualsHashCodeToString {

    private final Map<NottableString, List<NottableString>> backingMap = new LinkedHashMap<>();
    private final RegexStringMatcher regexStringMatcher;
    private final KeyMatchStyle keyMatchStyle;

    public NottableStringMultiMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, KeyMatchStyle keyMatchStyle, List<? extends KeyToMultiValue> entries) {
        this.keyMatchStyle = keyMatchStyle;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        for (KeyToMultiValue keyToMultiValue : entries) {
            backingMap.put(keyToMultiValue.getName(), keyToMultiValue.getValues());
        }
    }

    @VisibleForTesting
    public NottableStringMultiMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, KeyMatchStyle keyMatchStyle, NottableString[]... keyAndValues) {
        this.keyMatchStyle = keyMatchStyle;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        for (NottableString[] keyAndValue : keyAndValues) {
            if (keyAndValue.length > 0) {
                backingMap.put(keyAndValue[0], keyAndValue.length > 1 ? Arrays.asList(keyAndValue).subList(1, keyAndValue.length) : Collections.emptyList());
            }
        }
    }

    public KeyMatchStyle getKeyMatchStyle() {
        return keyMatchStyle;
    }

    public boolean containsAll(MockServerLogger mockServerLogger, MatchDifference context, NottableStringMultiMap subset) {
        switch (subset.keyMatchStyle) {
            case SUB_SET: {
                boolean isSubset = containsSubset(regexStringMatcher, subset.entryList(), entryList());
                if (!isSubset && context != null) {
                    context.addDifference(mockServerLogger, "multimap subset match failed subset:{}was not a subset of:{}", subset.entryList(), entryList());
                }
                return isSubset;
            }
            case MATCHING_KEY: {
                for (NottableString matcherKey : subset.backingMap.keySet()) {
                    List<NottableString> matchedValuesForKey = getAll(matcherKey);
                    if (matchedValuesForKey.isEmpty() && !matcherKey.isOptional()) {
                        if (context != null) {
                            context.addDifference(mockServerLogger, "multimap subset match failed subset:{}did not have expected key:{}", subset, matcherKey);
                        }
                        return false;
                    }

                    List<NottableString> matcherValuesForKey = subset.getAll(matcherKey);
                    for (NottableString matchedValue : matchedValuesForKey) {
                        boolean matchesValue = false;
                        for (NottableString matcherValue : matcherValuesForKey) {
                            if (regexStringMatcher.matches(mockServerLogger, context, matcherValue, matchedValue)) {
                                matchesValue = true;
                                break;
                            } else {
                                if (context != null) {
                                    context.addDifference(mockServerLogger, "multimap matching key match failed for key:{}", matcherKey);
                                }
                            }
                        }
                        if (!matchesValue) {
                            return false;
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    public boolean allKeysNotted() {
        if (!isEmpty()) {
            for (NottableString key : backingMap.keySet()) {
                if (!key.isNot()) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean allKeysOptional() {
        if (!isEmpty()) {
            for (NottableString key : backingMap.keySet()) {
                if (!key.isOptional()) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isEmpty() {
        return backingMap.isEmpty();
    }

    private List<NottableString> getAll(NottableString key) {
        if (!isEmpty()) {
            List<NottableString> values = new ArrayList<>();
            for (Map.Entry<NottableString, List<NottableString>> entry : backingMap.entrySet()) {
                if (regexStringMatcher.matches(key, entry.getKey())) {
                    values.addAll(entry.getValue());
                }
            }
            return values;
        } else {
            return Collections.emptyList();
        }
    }

    private List<ImmutableEntry> entryList() {
        if (!isEmpty()) {
            List<ImmutableEntry> entrySet = new ArrayList<>();
            for (Map.Entry<NottableString, List<NottableString>> entry : backingMap.entrySet()) {
                for (NottableString value : entry.getValue()) {
                    entrySet.add(entry(regexStringMatcher, entry.getKey(), value));
                }
            }
            return entrySet;
        } else {
            return Collections.emptyList();
        }
    }
}



