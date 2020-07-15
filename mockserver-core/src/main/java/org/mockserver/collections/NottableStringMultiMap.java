package org.mockserver.collections;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.NottableString;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockserver.collections.ImmutableEntry.entry;
import static org.mockserver.collections.ImmutableEntry.listsEqual;
import static org.mockserver.collections.SubSets.distinctSubSetsMap;
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
    private boolean noOptionals = true;

    public NottableStringMultiMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, KeyMatchStyle keyMatchStyle, List<? extends KeyToMultiValue> entries) {
        this.mockServerLogger = mockServerLogger;
        this.keyMatchStyle = keyMatchStyle;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        for (KeyToMultiValue keyToMultiValue : entries) {
            if (keyToMultiValue.getName().isOptional()) {
                noOptionals = false;
                if (keyToMultiValue.getValues().size() > 1) {
                    throw new IllegalArgumentException("multiple values for optional key are not allowed, value \"" + keyToMultiValue.getName() + "\" has values \"" + keyToMultiValue.getValues() + "\"");
                }
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
            if (nottableKey.isOptional()) {
                noOptionals = false;
                if (nottableValues.size() > 1) {
                    throw new IllegalArgumentException("multiple values for optional key are not allowed, key \"" + nottableKey + "\" has values \"" + nottableValues + "\"");
                }
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
            if (keysAndValue.getKey().isOptional()) {
                noOptionals = false;
                if (keysAndValue.getValue().size() > 1) {
                    throw new IllegalArgumentException("multiple values for optional key are not allowed, key \"" + keysAndValue.getKey() + "\" has values \"" + keysAndValue.getValue() + "\"");
                }
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
            if (nottableKey.isOptional()) {
                noOptionals = false;
                if (nottableValues.size() > 1) {
                    throw new IllegalArgumentException("multiple values for optional key are not allowed, value \"" + nottableKey + "\" has values \"" + nottableValues + "\"");
                }
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

    public boolean isNoOptionals() {
        return noOptionals;
    }

    public boolean containsAll(NottableStringMultiMap matcher) {
        return containsAll(matcher, null);
    }

    public boolean containsAll(NottableStringMultiMap matcher, String logCorrelationId) {
        switch (matcher.keyMatchStyle) {
            case SUB_SET: {
                List<ImmutableEntry> matchedEntries = entryList();
                Multimap<Integer, List<ImmutableEntry>> allMatchedSubSets
                    = distinctSubSetsMap(matchedEntries, ArrayListMultimap.create(), matchedEntries.size() - 1);

                if (MockServerLogger.isEnabled(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
                            .setCorrelationId(logCorrelationId)
                            .setMessageFormat("attempting to match subset from{}against multimap{}")
                            .setArguments(allMatchedSubSets, matcher.entryList())

                    );
                }

                if (isEmpty() && matcher.allKeysNotted()) {

                    return true;

                } else if (noOptionals && matcher.isNoOptionals()) {

                    // all non-optionals
                    List<ImmutableEntry> matcherEntries = matcher.entryList();
                    for (List<ImmutableEntry> matchedSubSet : allMatchedSubSets.get(matcherEntries.size())) {
                        if (listsEqual(matcherEntries, matchedSubSet)) {
                            if (MockServerLogger.isEnabled(TRACE)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(TRACE)
                                        .setCorrelationId(logCorrelationId)
                                        .setMessageFormat("multimap{}containsAll subset{}in{}")
                                        .setArguments(this, matchedSubSet, matcherEntries)

                                );
                            }
                            return true;
                        }
                    }
                    if (MockServerLogger.isEnabled(TRACE)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(TRACE)
                                .setCorrelationId(logCorrelationId)
                                .setMessageFormat("multimap{}containsAll found no subset equal to{}from{}")
                                .setArguments(this, matcherEntries, allMatchedSubSets)

                        );
                    }
                } else {

                    // some optionals exist
                    boolean result = false;

                    // first check non-optionals
                    List<ImmutableEntry> matcherEntriesWithoutOptionals = matcher.entryList().stream().filter(entry -> !entry.getKey().isOptional()).collect(Collectors.toList());
                    for (List<ImmutableEntry> matchedSubSet : allMatchedSubSets.get(matcherEntriesWithoutOptionals.size())) {
                        if (listsEqual(matcherEntriesWithoutOptionals, matchedSubSet)) {
                            if (MockServerLogger.isEnabled(TRACE)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(TRACE)
                                        .setCorrelationId(logCorrelationId)
                                        .setMessageFormat("multimap{}containsAll subset of non-optionals{}in{}")
                                        .setArguments(this, matchedSubSet, matcherEntriesWithoutOptionals)

                                );
                            }
                            result = true;
                        }
                    }

                    // then check optionals
                    if (result) {
                        List<ImmutableEntry> optionalMatcherEntries = matcher.entryList().stream().filter(entry -> entry.getKey().isOptional()).collect(Collectors.toList());
                        if (!optionalMatcherEntries.isEmpty()) {
                            Set<ImmutableEntry> matchedSubSet = new HashSet<>();
                            for (ImmutableEntry optionalMatcherEntry : optionalMatcherEntries) {
                                List<NottableString> matchedValuesForKey = getAll(optionalMatcherEntry.getKey());
                                boolean matchesValue = false;
                                if (matchedValuesForKey.isEmpty()) {
                                    matchesValue = true;
                                }
                                for (NottableString matchedValue : matchedValuesForKey) {
                                    if (regexStringMatcher.matches(optionalMatcherEntry.getValue(), matchedValue, true)) {
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
                                                .setMessageFormat("multimap{}matching by subset failed to match optional{}with any value from{}")
                                                .setArguments(this, optionalMatcherEntry, matchedValuesForKey)

                                        );
                                    }
                                    return false;
                                }
                            }
                            if (MockServerLogger.isEnabled(TRACE)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(TRACE)
                                        .setCorrelationId(logCorrelationId)
                                        .setMessageFormat("multimap{}containsAll subset of optionals{}in{}")
                                        .setArguments(this, matchedSubSet, optionalMatcherEntries)

                                );
                            }
                            return true;
                        } else {
                            return true;
                        }
                    }

                    if (MockServerLogger.isEnabled(TRACE)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(TRACE)
                                .setCorrelationId(logCorrelationId)
                                .setMessageFormat("multimap{}containsAll found no subset equal to{}from{}")
                                .setArguments(this, matcher.entryList(), matchedEntries)

                        );
                    }
                }
                return false;
            }
            case MATCHING_KEY: {
                for (NottableString matcherKey : matcher.backingMap.keySet()) {
                    List<NottableString> matcherValuesForKey = matcher.getAll(matcherKey);
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
                                        .setMessageFormat("multimap{}containsAll matching by key found non-matching value{}for{}")
                                        .setArguments(this, matchedValue, matcherValuesForKey)

                                );
                            }
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



