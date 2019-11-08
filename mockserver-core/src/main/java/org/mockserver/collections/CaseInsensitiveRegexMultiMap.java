package org.mockserver.collections;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.NottableString;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.*;

import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.NottableString.strings;

/**
 * MultiMap that uses case insensitive regex expression matching for keys and values
 *
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMap extends ObjectWithReflectiveEqualsHashCodeToString implements Map<NottableString, NottableString> {
    private final CaseInsensitiveNottableRegexListHashMap backingMap;

    private final RegexStringMatcher regexStringMatcher;

    public CaseInsensitiveRegexMultiMap(MockServerLogger mockServerLogger) {
        regexStringMatcher = new RegexStringMatcher(mockServerLogger);
        backingMap = new CaseInsensitiveNottableRegexListHashMap(mockServerLogger);
    }

    @VisibleForTesting
    public static CaseInsensitiveRegexMultiMap multiMap(String[]... keyAndValues) {
        CaseInsensitiveRegexMultiMap multiMap = new CaseInsensitiveRegexMultiMap(new MockServerLogger());
        for (String[] keyAndValue : keyAndValues) {
            for (int i = 1; i < keyAndValue.length; i++) {
                multiMap.put(keyAndValue[0], keyAndValue[i]);
            }
        }
        return multiMap;
    }

    @VisibleForTesting
    public static CaseInsensitiveRegexMultiMap multiMap(NottableString[]... keyAndValues) {
        CaseInsensitiveRegexMultiMap multiMap = new CaseInsensitiveRegexMultiMap(new MockServerLogger());
        for (NottableString[] keyAndValue : keyAndValues) {
            for (int i = 1; i < keyAndValue.length; i++) {
                multiMap.put(keyAndValue[0], keyAndValue[i]);
            }
        }
        return multiMap;
    }

    public static Entry<NottableString, NottableString> entry(String key, String value) {
        return new ImmutableEntry(key, value);
    }

    public boolean containsAll(CaseInsensitiveRegexMultiMap subSet) {
        if (isEmpty() && subSet.allKeysNotted()) {
            return true;
        } else {
            for (Entry<NottableString, NottableString> entry : subSet.entryList()) {
                if ((entry.getKey().isNot() || entry.getValue().isNot()) && containsKeyValue(entry.getKey().getValue(), entry.getValue().getValue())) {
                    Entry<NottableString, NottableString> matchingEntry = retrieveEntry(entry.getKey(), entry.getValue());
                    if (matchingEntry != null) {
                        return entry.getKey().isNot() == matchingEntry.getKey().isNot() && entry.getValue().isNot() == matchingEntry.getValue().isNot();
                    } else {
                        return false;
                    }
                } else if (!containsKeyValue(entry.getKey(), entry.getValue())) {
                    return false;
                }
            }
        }
        return true;
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

    private synchronized Entry<NottableString, NottableString> retrieveEntry(NottableString key, NottableString value) {
        if (!isEmpty()) {
            for (Entry<NottableString, NottableString> matcherEntry : entryList()) {
                if (regexStringMatcher.matches(value, matcherEntry.getValue(), true)
                    && regexStringMatcher.matches(key, matcherEntry.getKey(), true)) {
                    return matcherEntry;
                }
            }
        }
        return null;
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
                            if (regexStringMatcher.matches(keyValue, (NottableString) value, false)) {
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
        List<NottableString> list = Collections.synchronizedList(new ArrayList<NottableString>());
        for (Entry<NottableString, NottableString> entry : entryList()) {
            if (EqualsBuilder.reflectionEquals(entry.getKey(), key)) {
                list.add(entry.getValue());
            }
        }
        list.add(value);
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
            Collection<NottableString> values = new ArrayList<NottableString>();
            for (List<NottableString> valuesForKey : backingMap.values()) {
                values.addAll(valuesForKey);
            }
            return values;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public synchronized Set<Entry<NottableString, NottableString>> entrySet() {
        if (!isEmpty()) {
            Set<Entry<NottableString, NottableString>> entrySet = new LinkedHashSet<Entry<NottableString, NottableString>>();
            for (Entry<NottableString, List<NottableString>> entry : backingMap.entrySet()) {
                for (NottableString value : entry.getValue()) {
                    entrySet.add(new ImmutableEntry(entry.getKey(), value));
                }
            }
            return entrySet;
        } else {
            return Collections.emptySet();
        }
    }

    public synchronized List<Entry<NottableString, NottableString>> entryList() {
        if (!isEmpty()) {
            List<Entry<NottableString, NottableString>> entrySet = new ArrayList<>();
            for (Entry<NottableString, List<NottableString>> entry : backingMap.entrySet()) {
                for (NottableString value : entry.getValue()) {
                    entrySet.add(new ImmutableEntry(entry.getKey(), value));
                }
            }
            return entrySet;
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

    static class ImmutableEntry extends ObjectWithReflectiveEqualsHashCodeToString implements Entry<NottableString, NottableString> {
        private final NottableString key;
        private final NottableString value;

        ImmutableEntry(String key, String value) {
            this.key = string(key);
            this.value = string(value);
        }

        ImmutableEntry(NottableString key, NottableString value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public NottableString getKey() {
            return key;
        }

        @Override
        public NottableString getValue() {
            return value;
        }

        @Override
        public NottableString setValue(NottableString value) {
            throw new UnsupportedOperationException("ImmutableEntry is immutable");
        }
    }

}



