package org.mockserver.collections;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.NottableString;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.*;

import static org.mockserver.collections.ImmutableEntry.*;
import static org.mockserver.collections.SubSets.distinctSubSetsList;
import static org.mockserver.collections.SubSets.distinctSubSetsMap;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.NottableString.strings;
import static org.slf4j.event.Level.DEBUG;

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
    private boolean noOptionals = true;

    public CaseInsensitiveRegexMultiMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher) {
        this.mockServerLogger = mockServerLogger;
        regexStringMatcher = new RegexStringMatcher(mockServerLogger, controlPlaneMatcher);
        backingMap = new CaseInsensitiveNottableRegexListHashMap(mockServerLogger, controlPlaneMatcher);
    }

    @VisibleForTesting
    public static CaseInsensitiveRegexMultiMap multiMap(boolean controlPlaneMatcher, String[]... keyAndValues) {
        CaseInsensitiveRegexMultiMap multiMap = new CaseInsensitiveRegexMultiMap(new MockServerLogger(), controlPlaneMatcher);
        for (String[] keyAndValue : keyAndValues) {
            for (int i = 1; i < keyAndValue.length; i++) {
                multiMap.put(keyAndValue[0], keyAndValue[i]);
            }
        }
        return multiMap;
    }

    @VisibleForTesting
    public static CaseInsensitiveRegexMultiMap multiMap(boolean controlPlaneMatcher, NottableString[]... keyAndValues) {
        CaseInsensitiveRegexMultiMap multiMap = new CaseInsensitiveRegexMultiMap(new MockServerLogger(), controlPlaneMatcher);
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

    public boolean containsAll(CaseInsensitiveRegexMultiMap matcherSubSet) {
        if (isEmpty() && matcherSubSet.allKeysNotted()) {
            return true;
        } else if (noOptionals && matcherSubSet.isNoOptionals()) {
            List<ImmutableEntry> matchedEntries = entryList();
            Multimap<Integer, List<ImmutableEntry>> allMatchedSubSets
                = distinctSubSetsMap(matchedEntries, ArrayListMultimap.create(), matchedEntries.size() - 1);

            List<ImmutableEntry> matcherEntries = matcherSubSet.entryList();
            for (List<ImmutableEntry> matchedSubSet : allMatchedSubSets.get(matcherEntries.size())) {
                if (listsEqual(matcherEntries, matchedSubSet)) {
                    if (MockServerLogger.isEnabled(DEBUG)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(DEBUG)
                                .setMessageFormat("multimap{}containsAll matched subset{}with{}")
                                .setArguments(this, matcherEntries, matchedSubSet)

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
            List<ImmutableEntry> matchedEntries = entryList();
            List<List<ImmutableEntry>> allMatchedSubSets
                = distinctSubSetsList(matchedEntries, new ArrayList<>(), matchedEntries.size() - 1);

            List<ImmutableEntry> matcherEntries = matcherSubSet.entryList();
            for (List<ImmutableEntry> matchedSubSet : allMatchedSubSets) {
                if (listsEqualWithOptionals(matcherEntries, matchedSubSet)) {
                    if (MockServerLogger.isEnabled(DEBUG)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(DEBUG)
                                .setMessageFormat("multimap with optionals{}containsAll matched subset{}with{}")
                                .setArguments(this, matcherEntries, matchedSubSet)

                        );
                    }
                    return true;
                }
            }
            if (MockServerLogger.isEnabled(DEBUG)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(DEBUG)
                        .setMessageFormat("multimap with optionals{}containsAll found no subset equal to{}from{}")
                        .setArguments(this, matcherEntries, allMatchedSubSets)

                );
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
            if (EqualsBuilder.reflectionEquals(entry.getKey(), key, "key")) {
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



