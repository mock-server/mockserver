package org.mockserver.collections;

import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.NottableString;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.*;

import static org.mockserver.model.NottableString.string;

/**
 * MultiMap that uses case insensitive regex expression matching for keys and values
 *
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMap extends ObjectWithReflectiveEqualsHashCodeToString implements Map<NottableString, String> {
    private final CaseInsensitiveRegexHashMap<List<String>> backingMap = new CaseInsensitiveRegexHashMap<List<String>>();

    @Override
    public synchronized int size() {
        return backingMap.size();
    }

    @Override
    public synchronized boolean isEmpty() {
        return backingMap.isEmpty();
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        return backingMap.containsKey(key);
    }

    public boolean containsAll(CaseInsensitiveRegexMultiMap subSet) {
        for (NottableString subSetKey : subSet.keySet()) {
            if (!containsKey(subSetKey)) { // check if sub-set key exists in super-set
                return false;
            } else { // check if sub-set value matches at least one super-set value using regex
                for (String subSetValue : subSet.getAll(subSetKey)) {
                    if (!containsKeyValue(subSetKey, subSetValue)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public synchronized boolean containsKeyValue(NottableString key, String value) {
        boolean result = false;

        outermost:
        for (NottableString matcherKey : backingMap.keySet()) {
            for (List<String> allMatcherKeyValues : backingMap.getAll(matcherKey)) {
                for (String matcherKeyValue : allMatcherKeyValues) {
                    if (RegexStringMatcher.matches(matcherKey.getValue(), key.getValue(), true) && RegexStringMatcher.matches(value, matcherKeyValue, false)) {
                        result = true;
                        break outermost;
                    }
                }
            }
        }

        return key.isNot() != result;
    }

    @Override
    public synchronized boolean containsValue(Object value) {
        if (value instanceof String) {
            for (NottableString key : backingMap.keySet()) {
                for (List<String> allKeyValues : backingMap.getAll(key)) {
                    for (String keyValue : allKeyValues) {
                        if (RegexStringMatcher.matches(keyValue, (String) value, false)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public synchronized String get(Object key) {
        List<String> values = backingMap.get(key);
        if (values != null && values.size() > 0) {
            return values.get(0);
        } else {
            return null;
        }
    }

    public synchronized List<String> getAll(String key) {
        return getAll(string(key));
    }

    public synchronized List<String> getAll(Object key) {
        List<String> all = new ArrayList<String>();
        for (List<String> subList : backingMap.getAll(key)) {
            all.addAll(subList);
        }
        return all;
    }

    public synchronized String put(String key, String value) {
        return put(string(key), value);
    }

    @Override
    public synchronized String put(NottableString key, String value) {
        List<String> list = Collections.synchronizedList(new ArrayList<String>());
        if (containsKey(key) && backingMap.get(key) != null) {
            list.addAll(backingMap.get(key));
        }
        list.add(value);
        backingMap.put(key, list);
        return value;
    }

    public synchronized List<String> put(String key, List<String> values) {
        return put(string(key), values);
    }

    public synchronized List<String> put(NottableString key, List<String> values) {
        if (containsKey(key)) {
            for (String value : values) {
                put(key, value);
            }
        } else {
            backingMap.put(key, values);
        }
        return values;
    }

    public void putValuesForNewKeys(CaseInsensitiveRegexMultiMap multiMap) {
        for (NottableString key : multiMap.keySet()) {
            if (!containsKey(key)) {
                backingMap.put(key, multiMap.getAll(key));
            }
        }
    }

    @Override
    public synchronized String remove(Object key) {
        List<String> values = backingMap.get(key);
        if (values != null && values.size() > 0) {
            return values.remove(0);
        } else {
            return null;
        }
    }

    public synchronized List<String> removeAll(NottableString key) {
        return backingMap.remove(key);
    }

    public synchronized List<String> removeAll(String key) {
        return backingMap.remove(key);
    }

    @Override
    public synchronized void putAll(Map<? extends NottableString, ? extends String> map) {
        for (Entry<? extends NottableString, ? extends String> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public synchronized void clear() {
        backingMap.clear();
    }

    @Override
    public synchronized Set<NottableString> keySet() {
        return backingMap.keySet();
    }

    @Override
    public synchronized Collection<String> values() {
        Collection<String> values = new ArrayList<String>();
        for (List<String> valuesForKey : backingMap.values()) {
            values.addAll(valuesForKey);
        }
        return values;
    }

    @Override
    public synchronized Set<Entry<NottableString, String>> entrySet() {
        Set<Entry<NottableString, String>> entrySet = new LinkedHashSet<Entry<NottableString, String>>();
        for (Entry<NottableString, List<String>> entry : backingMap.entrySet()) {
            for (String value : entry.getValue()) {
                entrySet.add(new ImmutableEntry(entry.getKey(), value));
            }
        }
        return entrySet;
    }

    class ImmutableEntry extends ObjectWithReflectiveEqualsHashCodeToString implements Entry<NottableString, String> {
        private final NottableString key;
        private final String value;

        ImmutableEntry(String key, String value) {
            this.key = string(key);
            this.value = value;
        }

        ImmutableEntry(NottableString key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public NottableString getKey() {
            return key;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String setValue(String value) {
            throw new UnsupportedOperationException("ImmutableEntry is immutable");
        }
    }

}



