package org.mockserver.collections;

import org.mockserver.matchers.RegexStringMatcher;

import java.util.*;

/**
 * Map that uses case insensitive regex expression matching for keys
 *
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMap<V> extends LinkedHashMap<String, V> implements Map<String, V> {
    static final long serialVersionUID = 1530623482381786485L;

    public boolean containsAll(CaseInsensitiveRegexHashMap<String> subSet) {
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
        for (String matcherKey : keySet()) {
            for (Object matcherKeyValue : getAll(matcherKey)) {
                if (matcherKeyValue instanceof String) {
                    if (RegexStringMatcher.matches(matcherKey, key, true) && RegexStringMatcher.matches(value, (String) matcherKeyValue, false)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        if (key instanceof String) {
            if (super.containsKey(key)) {
                return true;
            } else {
                for (String keyToCompare : keySet()) {
                    if (RegexStringMatcher.matches((String) key, keyToCompare, true)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public synchronized V get(Object key) {
        if (key instanceof String) {
            if (super.get(key) != null) {
                return super.get(key);
            } else {
                for (String keyToCompare : keySet()) {
                    if (RegexStringMatcher.matches((String) key, keyToCompare, true)) {
                        return super.get(keyToCompare);
                    }
                }
            }
        }
        return null;
    }

    public synchronized Collection<V> getAll(Object key) {
        List<V> values = new ArrayList<V>();
        if (key instanceof String) {
            for (String keyToCompare : keySet()) {
                if (RegexStringMatcher.matches((String) key, keyToCompare, true)) {
                    values.add(super.get(keyToCompare));
                }
            }
        }
        return values;
    }

    @Override
    public synchronized V remove(Object key) {
        if (key instanceof String) {
            if (super.get(key) != null) {
                return super.remove(key);
            } else {
                for (String keyToCompare : keySet()) {
                    if (RegexStringMatcher.matches((String) key, keyToCompare, true)) {
                        return super.remove(keyToCompare);
                    }
                }
            }
        }
        return null;
    }
}
