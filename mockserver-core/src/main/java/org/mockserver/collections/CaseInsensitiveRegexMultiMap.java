package org.mockserver.collections;

import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.*;

/**
 * MultiMap that uses case insensitive regex expression matching for keys and values
 *
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMap extends ObjectWithReflectiveEqualsHashCodeToString implements Map<String, String> {
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
        for (String subSetKey : subSet.keySet()) {
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

    public synchronized boolean containsKeyValue(String key, String value) {
        for (String matcherKey : backingMap.keySet()) {
            for (List<String> allMatcherKeyValues : backingMap.getAll(matcherKey)) {
                for (String matcherKeyValue : allMatcherKeyValues) {
                    if (RegexStringMatcher.matches(matcherKey, key, true) && RegexStringMatcher.matches(value, matcherKeyValue, false)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public synchronized boolean containsValue(Object value) {
        if (value instanceof String) {
            for (String key : backingMap.keySet()) {
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

    public synchronized List<String> getAll(Object key) {
        List<String> all = new ArrayList<String>();
        for (List<String> subList : backingMap.getAll(key)) {
            all.addAll(subList);
        }
        return all;
    }

    @Override
    public synchronized String put(String key, String value) {
        List<String> list = Collections.synchronizedList(new ArrayList<String>());
        if (containsKey(key)) {
            list.addAll(backingMap.get(key));
        }
        list.add(value);
        backingMap.put(key, list);
        return value;
    }

    public synchronized List<String> put(String key, List<String> values) {
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
        for (String key : multiMap.keySet()) {
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

    public synchronized List<String> removeAll(String key) {
        return backingMap.remove(key);
    }

    @Override
    public synchronized void putAll(Map<? extends String, ? extends String> map) {
        for (Entry<? extends String, ? extends String> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public synchronized void clear() {
        backingMap.clear();
    }

    @Override
    public synchronized Set<String> keySet() {
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
    public synchronized Set<Entry<String, String>> entrySet() {
        Set<Entry<String, String>> entrySet = new LinkedHashSet<Entry<String, String>>();
        for (Entry<String, List<String>> entry : backingMap.entrySet()) {
            for (String value : entry.getValue()) {
                entrySet.add(new ImmutableEntry(entry.getKey(), value));
            }
        }
        return entrySet;
    }

    class ImmutableEntry extends ObjectWithReflectiveEqualsHashCodeToString implements Entry<String, String> {
        private final String key;
        private final String value;

        ImmutableEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
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



