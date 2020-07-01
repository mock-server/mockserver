package org.mockserver.collections;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.NottableString;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockserver.collections.ImmutableEntry.entry;
import static org.mockserver.collections.ImmutableEntry.listsEqual;
import static org.mockserver.collections.SubSets.distinctSubSetsMap;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.NottableString.strings;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.TRACE;

/**
 * MultiMap that uses case insensitive regex expression matching for keys and values
 *
 * @author jamesdbloom
 */
@SuppressWarnings("NullableProblems")
public class CaseInsensitiveRegexMultiMap extends ObjectWithReflectiveEqualsHashCodeToString implements Map<NottableString, NottableString> {
    private final CaseInsensitiveNottableRegexListHashMap backingMap;

    private final RegexStringMatcher regexStringMatcher;
    private final MockServerLogger mockServerLogger;
    private final KeyMatchStyle keyMatchStyle;
    private boolean noOptionals = true;

    public CaseInsensitiveRegexMultiMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher) {
        this(mockServerLogger, controlPlaneMatcher, KeyMatchStyle.SUB_SET);
    }

    public CaseInsensitiveRegexMultiMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher, KeyMatchStyle keyMatchStyle) {
        this.mockServerLogger = mockServerLogger;
        this.keyMatchStyle = keyMatchStyle;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        backingMap = new CaseInsensitiveNottableRegexListHashMap(mockServerLogger, controlPlaneMatcher);
    }

    @VisibleForTesting
    public static CaseInsensitiveRegexMultiMap multiMap(boolean controlPlaneMatcher, KeyMatchStyle keyMatchStyle, String[]... keyAndValues) {
        CaseInsensitiveRegexMultiMap multiMap = new CaseInsensitiveRegexMultiMap(new MockServerLogger(), controlPlaneMatcher, keyMatchStyle);
        for (String[] keyAndValue : keyAndValues) {
            for (int i = 1; i < keyAndValue.length; i++) {
                multiMap.put(keyAndValue[0], keyAndValue[i]);
            }
        }
        return multiMap;
    }

    @VisibleForTesting
    public static CaseInsensitiveRegexMultiMap multiMap(boolean controlPlaneMatcher, KeyMatchStyle keyMatchStyle, NottableString[]... keyAndValues) {
        CaseInsensitiveRegexMultiMap multiMap = new CaseInsensitiveRegexMultiMap(new MockServerLogger(), controlPlaneMatcher, keyMatchStyle);
        for (NottableString[] keyAndValue : keyAndValues) {
            for (int i = 1; i < keyAndValue.length; i++) {
                multiMap.put(keyAndValue[0], keyAndValue[i]);
            }
        }
        return multiMap;
    }

    public boolean isNoOptionals() {
        return noOptionals;
    }

    public boolean containsAll(CaseInsensitiveRegexMultiMap matcher) {
        switch (matcher.keyMatchStyle) {
            case SUB_SET: {
                List<ImmutableEntry> matchedEntries = entryList();
                Multimap<Integer, List<ImmutableEntry>> allMatchedSubSets
                    = distinctSubSetsMap(matchedEntries, ArrayListMultimap.create(), matchedEntries.size() - 1);

                if (MockServerLogger.isEnabled(TRACE)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(TRACE)
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
                            if (MockServerLogger.isEnabled(DEBUG)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(DEBUG)
                                        .setMessageFormat("multimap{}containsAll subset{}in{}")
                                        .setArguments(this, matchedSubSet, matcherEntries)

                                );
                            }
                            return true;
                        }
                    }
                    if (MockServerLogger.isEnabled(DEBUG)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(DEBUG)
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
                            if (MockServerLogger.isEnabled(DEBUG)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(DEBUG)
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
                                    if (MockServerLogger.isEnabled(DEBUG)) {
                                        mockServerLogger.logEvent(
                                            new LogEntry()
                                                .setLogLevel(DEBUG)
                                                .setMessageFormat("multimap{}matching by subset failed to match optional{}with any value from{}")
                                                .setArguments(this, optionalMatcherEntry, matchedValuesForKey)

                                        );
                                    }
                                    return false;
                                }
                            }
                            if (MockServerLogger.isEnabled(DEBUG)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(DEBUG)
                                        .setMessageFormat("multimap{}containsAll subset of optionals{}in{}")
                                        .setArguments(this, matchedSubSet, optionalMatcherEntries)

                                );
                            }
                            return true;
                        } else {
                            return true;
                        }
                    }

                    if (MockServerLogger.isEnabled(DEBUG)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(DEBUG)
                                .setMessageFormat("multimap{}containsAll found no subset equal to{}from{}")
                                .setArguments(this, matcher.entryList(), matchedEntries)

                        );
                    }
                }
                return false;
            }
            case MATCHING_KEY: {
                for (NottableString matcherKey : matcher.keySet()) {
                    List<NottableString> matcherValuesForKey = matcher.getAll(matcherKey);
                    List<NottableString> matchedValuesForKey = getAll(matcherKey);
                    if (matchedValuesForKey.isEmpty() && !matcherKey.isOptional()) {
                        if (MockServerLogger.isEnabled(DEBUG)) {
                            mockServerLogger.logEvent(
                                new LogEntry()
                                    .setLogLevel(DEBUG)
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
                            if (MockServerLogger.isEnabled(DEBUG)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(DEBUG)
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
            for (NottableString key : keySet()) {
                if (!key.isNot()) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean allKeysOptional() {
        if (!isEmpty()) {
            for (NottableString key : keySet()) {
                if (!key.isOptional()) {
                    return false;
                }
            }
        }
        return true;
    }

    public synchronized boolean containsKeyValue(String key, String value) {
        return containsKeyValue(string(key), string(value));
    }

    public synchronized boolean containsKeyValue(NottableString key, NottableString value) {
        if (!isEmpty()) {
            for (NottableString valueToMatch : getAll(key)) {
                if (regexStringMatcher.matches(value, valueToMatch, true)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        return backingMap.containsKey(key);
    }

    @Override
    public synchronized boolean containsValue(Object value) {
        if (!isEmpty()) {
            if (value instanceof NottableString) {
                for (NottableString key : backingMap.keySet()) {
                    for (List<NottableString> allKeyValues : backingMap.getAll(key)) {
                        for (NottableString keyValue : allKeyValues) {
                            if (regexStringMatcher.matches(keyValue, (NottableString) value, true)) {
                                return true;
                            }
                        }
                    }
                }
            } else if (value instanceof String) {
                return containsValue(string((String) value));
            }
        }
        return false;
    }

    @Override
    public synchronized NottableString get(Object key) {
        if (!isEmpty()) {
            if (key instanceof String) {
                return get(string((String) key));
            } else {
                List<NottableString> values = backingMap.get(key);
                if (values != null && values.size() > 0) {
                    return values.get(0);
                } else {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    public synchronized List<NottableString> getAll(String key) {
        return getAll(string(key));
    }

    public synchronized List<NottableString> getAll(NottableString key) {
        if (!isEmpty()) {
            List<NottableString> all = new ArrayList<>();
            for (List<NottableString> subList : backingMap.getAll(key)) {
                all.addAll(subList);
            }
            return all;
        } else {
            return Collections.emptyList();
        }
    }

    public synchronized NottableString put(String key, String value) {
        return put(string(key), string(value));
    }

    @Override
    public synchronized NottableString put(NottableString key, NottableString value) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        List<NottableString> list = Collections.synchronizedList(new ArrayList<>());
        for (ImmutableEntry entry : entryList()) {
            // TODO(jamesdbloom) can this use of reflection equals be optimised
            if (EqualsBuilder.reflectionEquals(entry.getKey(), key, "key")) {
                if (key.isOptional()) {
                    throw new IllegalArgumentException("multiple values for optional key are not allowed, value \"" + entry.getValue() + "\" already exists for \"" + key + "\"");
                }
                list.add(entry.getValue());
            }
        }
        list.add(value);
        if (key.isOptional()) {
            noOptionals = false;
        }
        backingMap.put(key, list);
        return value;
    }

    public synchronized List<NottableString> put(String key, List<String> values) {
        return put(string(key), strings(values));
    }

    public synchronized List<NottableString> put(NottableString key, List<NottableString> values) {
        if (containsKey(key)) {
            for (NottableString value : values) {
                put(key, value);
            }
        } else {
            backingMap.put(key, values);
        }
        return values;
    }

    @Override
    @SuppressWarnings("SuspiciousMethodCalls")
    public synchronized NottableString remove(Object key) {
        if (!isEmpty()) {
            if (key instanceof String) {
                return remove(string((String) key));
            } else {
                List<NottableString> values = backingMap.get(key);
                if (values != null && values.size() > 0) {
                    NottableString removed = values.remove(0);
                    if (values.size() == 0) {
                        backingMap.remove(key);
                    }
                    return removed;
                } else {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    public synchronized List<NottableString> removeAll(NottableString key) {
        return backingMap.remove(key);
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    public synchronized List<NottableString> removeAll(String key) {
        return backingMap.remove(key);
    }

    @Override
    public synchronized void putAll(Map<? extends NottableString, ? extends NottableString> map) {
        for (Entry<? extends NottableString, ? extends NottableString> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public synchronized void clear() {
        backingMap.clear();
    }

    @Override
    public synchronized Set<NottableString> keySet() {
        if (!isEmpty()) {
            return backingMap.keySet();
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public synchronized Collection<NottableString> values() {
        if (!isEmpty()) {
            Collection<NottableString> values = new ArrayList<>();
            for (List<NottableString> valuesForKey : backingMap.values()) {
                values.addAll(valuesForKey);
            }
            return values;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public synchronized int size() {
        return backingMap.size();
    }

    @Override
    public synchronized boolean isEmpty() {
        return backingMap.isEmpty();
    }

    @Override
    public synchronized Set<Entry<NottableString, NottableString>> entrySet() {
        if (!isEmpty()) {
            Set<Entry<NottableString, NottableString>> entrySet = new LinkedHashSet<>();
            for (Entry<NottableString, List<NottableString>> entry : backingMap.entrySet()) {
                for (NottableString value : entry.getValue()) {
                    entrySet.add(entry(regexStringMatcher, entry.getKey(), value));
                }
            }
            return entrySet;
        } else {
            return Collections.emptySet();
        }
    }

    public synchronized List<ImmutableEntry> entryList() {
        if (!isEmpty()) {
            List<ImmutableEntry> entrySet = new ArrayList<>();
            for (Entry<NottableString, List<NottableString>> entry : backingMap.entrySet()) {
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



