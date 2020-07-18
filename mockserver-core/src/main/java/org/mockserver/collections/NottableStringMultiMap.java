package org.mockserver.collections;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.NottableString;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.*;

import static org.mockserver.collections.ImmutableEntry.entry;
import static org.mockserver.collections.SubSetMatcher.containsSubset;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.NottableString.strings;
import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
public class NottableStringMultiMap extends ObjectWithReflectiveEqualsHashCodeToString {

    private final Map<NottableString, List<NottableString>> backingMap = new LinkedHashMap<>();
    private final RegexStringMatcher regexStringMatcher;
    private final MockServerLogger mockServerLogger;
    private final KeyMatchStyle keyMatchStyle;

    public NottableStringMultiMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, KeyMatchStyle keyMatchStyle, List<? extends KeyToMultiValue> entries) {
        this.mockServerLogger = mockServerLogger;
        this.keyMatchStyle = keyMatchStyle;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        for (KeyToMultiValue keyToMultiValue : entries) {
            if (keyToMultiValue.getName().isOptional() && keyToMultiValue.getValues().size() > 1) {
                throw new IllegalArgumentException("multiple values for optional key are not allowed, value \"" + keyToMultiValue.getName() + "\" has values \"" + keyToMultiValue.getValues() + "\"");
            }
            backingMap.put(keyToMultiValue.getName(), keyToMultiValue.getValues());
        }
    }

    @VisibleForTesting
    public NottableStringMultiMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, KeyMatchStyle keyMatchStyle, String... keyAndValues) {
        this.mockServerLogger = mockServerLogger;
        this.keyMatchStyle = keyMatchStyle;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        Map<String, List<String>> groupedValues = new LinkedHashMap<>();
        for (int i = 0; i < keyAndValues.length - 1; i += 2) {
            if (groupedValues.containsKey(keyAndValues[i])) {
                groupedValues.get(keyAndValues[i]).add(keyAndValues[i + 1]);
            } else {
                groupedValues.put(keyAndValues[i], new ArrayList<>(Collections.singletonList(keyAndValues[i + 1])));
            }
        }
        for (Map.Entry<String, List<String>> keysAndValue : groupedValues.entrySet()) {
            NottableString nottableKey = string(keysAndValue.getKey());
            List<NottableString> nottableValues = strings(keysAndValue.getValue());
            if (nottableKey.isOptional() && nottableValues.size() > 1) {
                throw new IllegalArgumentException("multiple values for optional key are not allowed, key \"" + nottableKey + "\" has values \"" + nottableValues + "\"");
            }
            backingMap.put(nottableKey, nottableValues);
        }
    }

    @VisibleForTesting
    public NottableStringMultiMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, KeyMatchStyle keyMatchStyle, NottableString[]... keyAndValues) {
        this.mockServerLogger = mockServerLogger;
        this.keyMatchStyle = keyMatchStyle;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        Map<NottableString, List<NottableString>> groupedValues = new LinkedHashMap<>();
        for (NottableString[] keyAndValue : keyAndValues) {
            if (keyAndValue.length > 1) {
                groupedValues.put(keyAndValue[0], Arrays.asList(keyAndValue).subList(1, keyAndValue.length));
            }
        }
        for (Map.Entry<NottableString, List<NottableString>> keysAndValue : groupedValues.entrySet()) {
            if (keysAndValue.getKey().isOptional() && keysAndValue.getValue().size() > 1) {
                throw new IllegalArgumentException("multiple values for optional key are not allowed, key \"" + keysAndValue.getKey() + "\" has values \"" + keysAndValue.getValue() + "\"");
            }
            backingMap.put(keysAndValue.getKey(), keysAndValue.getValue());
        }
    }

    // TODO(jamesdbloom) remove once tests are cleaned up
    @VisibleForTesting
    public NottableStringMultiMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, KeyMatchStyle keyMatchStyle, String[]... keyAndValues) {
        this.mockServerLogger = mockServerLogger;
        this.keyMatchStyle = keyMatchStyle;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        Map<String, List<String>> groupedValues = new LinkedHashMap<>();
        for (String[] keyAndValue : keyAndValues) {
            groupedValues.put(keyAndValue[0], Arrays.asList(keyAndValue).subList(1, keyAndValue.length));
        }
        for (Map.Entry<String, List<String>> keysAndValue : groupedValues.entrySet()) {
            NottableString nottableKey = string(keysAndValue.getKey());
            List<NottableString> nottableValues = strings(keysAndValue.getValue());
            if (nottableKey.isOptional() && nottableValues.size() > 1) {
                throw new IllegalArgumentException("multiple values for optional key are not allowed, value \"" + nottableKey + "\" has values \"" + nottableValues + "\"");
            }
            backingMap.put(nottableKey, nottableValues);
        }
    }

    // TODO(jamesdbloom) remove once tests are cleaned up
    @VisibleForTesting
    public static NottableStringMultiMap multiMap(boolean controlPlaneMatcher, KeyMatchStyle keyMatchStyle, String[]... keyAndValues) {
        return new NottableStringMultiMap(new MockServerLogger(), controlPlaneMatcher, keyMatchStyle, keyAndValues);
    }

    // TODO(jamesdbloom) remove once tests are cleaned up
    @VisibleForTesting
    public static NottableStringMultiMap multiMap(boolean controlPlaneMatcher, KeyMatchStyle keyMatchStyle, String... keyAndValues) {
        return new NottableStringMultiMap(new MockServerLogger(), controlPlaneMatcher, keyMatchStyle, keyAndValues);
    }

    // TODO(jamesdbloom) remove once tests are cleaned up
    @VisibleForTesting
    public static NottableStringMultiMap multiMap(boolean controlPlaneMatcher, KeyMatchStyle keyMatchStyle, NottableString[]... keyAndValues) {
        return new NottableStringMultiMap(new MockServerLogger(), controlPlaneMatcher, keyMatchStyle, keyAndValues);
    }

    public KeyMatchStyle getKeyMatchStyle() {
        return keyMatchStyle;
    }

    public boolean containsAll(NottableStringMultiMap matcher) {
        return containsAll(matcher, null);
    }

    // TODO(jamesdbloom) check matching key
    public boolean containsAll(NottableStringMultiMap subset, String logCorrelationId) {
        switch (subset.keyMatchStyle) {
            case SUB_SET: {
                return containsSubset(regexStringMatcher, subset.entryList(), entryList());
            }
            case MATCHING_KEY: {
                for (NottableString matcherKey : subset.backingMap.keySet()) {
                    List<NottableString> matcherValuesForKey = subset.getAll(matcherKey);
                    List<NottableString> matchedValuesForKey = getAll(matcherKey);
                    if (matchedValuesForKey.isEmpty() && !matcherKey.isOptional()) {
                        if (MockServerLogger.isEnabled(TRACE)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(TRACE)
                                    .setCorrelationId(logCorrelationId)
                                    .setMessageFormat("multimap{}containsAll matching by key found no matching values for{}")
                                    .setArguments(this, matcherKey)

                            );
                        }
                        return false;
                    }

                    for (NottableString matchedValue : matchedValuesForKey) {
                        boolean matchesValue = false;
                        for (NottableString matcherValue : matcherValuesForKey) {
                            if (regexStringMatcher.matches(matcherValue, matchedValue, true)) {
                                matchesValue = true;
                                break;
                            }
                        }
                        if (!matchesValue) {
                            if (MockServerLogger.isEnabled(TRACE)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(TRACE)
                                        .setCorrelationId(logCorrelationId)
                                        .setMessageFormat("multimap{}containsAll matching by key{}found matched value{}does not-match one value in{}")
                                        .setArguments(this, matcherKey, matchedValue, matcherValuesForKey)

                                );
                            }
                            return false;
                        } else {
                            if (MockServerLogger.isEnabled(TRACE)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(TRACE)
                                        .setCorrelationId(logCorrelationId)
                                        .setMessageFormat("multimap{}containsAll matching by key{}found matched value{}matches one value in{}")
                                        .setArguments(this, matcherKey, matchedValue, matcherValuesForKey)

                                );
                            }
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
                if (regexStringMatcher.matches(key, entry.getKey(), true)) {
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



