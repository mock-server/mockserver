package org.mockserver.collections;

import org.mockserver.matchers.RegexStringMatcher;

import java.util.*;

import static org.mockserver.collections.NottableKey.nottableKey;

/**
 * Map that uses case insensitive regex expression matching for keys
 *
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMap<V> extends LinkedHashMap<NottableKey, V> implements Map<NottableKey, V> {
    static final long serialVersionUID = 1530623482381786485L;

    public boolean containsAll(CaseInsensitiveRegexHashMap<String> subSet) {
        for (NottableKey subSetKey : subSet.keySet()) {
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

    public synchronized boolean containsKeyValue(NottableKey key, String value) {
        boolean result = false;

        outermost:
        for (NottableKey matcherKey : keySet()) {
            for (Object matcherKeyValue : getAll(matcherKey)) {
                if (matcherKeyValue instanceof String) {
                    if (RegexStringMatcher.matches(matcherKey.getValue(), key.getValue(), true) && RegexStringMatcher.matches(value, (String) matcherKeyValue, false)) {
                        result = true;
                        break outermost;
                    }
                }
            }
        }

        return key.isNot() != result;
    }

    public synchronized V put(String key, V value) {
        return super.put(nottableKey(key), value);
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        boolean result = false;

        if (key instanceof NottableKey) {
            NottableKey nottableKey = (NottableKey) key;
            if (super.containsKey(key)) {
                return true;
            } else {
                for (NottableKey keyToCompare : keySet()) {
                    if (RegexStringMatcher.matches(nottableKey.getValue(), keyToCompare.getValue(), true)) {
                        result = true;
                        break;
                    }
                }
            }

            result = nottableKey.isNot() != result;

        } else if (key instanceof String) {
            result = containsKey(nottableKey((String) key));
        }

        return result;
    }

    @Override
    public synchronized V get(Object key) {
        if (key instanceof NottableKey) {
            if (super.get(key) != null) {
                return super.get(key);
            } else {
                NottableKey nottableKey = (NottableKey) key;
                for (NottableKey keyToCompare : keySet()) {
                    if (nottableKey.isNot() != RegexStringMatcher.matches(nottableKey.getValue(), keyToCompare.getValue(), true)) {
                        return super.get(keyToCompare);
                    }
                }
            }
        } else if (key instanceof String) {
            return get(nottableKey((String) key));
        }
        return null;
    }

    public synchronized Collection<V> getAll(Object key) {
        List<V> values = new ArrayList<V>();
        if (key instanceof NottableKey) {
            NottableKey nottableKey = (NottableKey) key;
            for (NottableKey keyToCompare : keySet()) {
                if (nottableKey.isNot() != RegexStringMatcher.matches(nottableKey.getValue(), keyToCompare.getValue(), true)) {
                    values.add(super.get(keyToCompare));
                }
            }
        } else if (key instanceof String) {
            return getAll(nottableKey((String) key));
        }
        return values;
    }

    @Override
    public synchronized V remove(Object key) {
        if (key instanceof NottableKey) {
            if (super.get(key) != null) {
                return super.remove(key);
            } else {
                NottableKey nottableKey = (NottableKey) key;
                for (NottableKey keyToCompare : keySet()) {
                    if (nottableKey.isNot() != RegexStringMatcher.matches(nottableKey.getValue(), keyToCompare.getValue(), true)) {
                        return super.remove(keyToCompare);
                    }
                }
            }
        } else if (key instanceof String) {
            return remove(nottableKey((String) key));
        }
        return null;
    }
}
