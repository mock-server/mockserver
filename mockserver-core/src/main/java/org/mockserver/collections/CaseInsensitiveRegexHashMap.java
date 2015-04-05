package org.mockserver.collections;

import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.NottableString;

import java.util.*;

import static org.mockserver.model.NottableString.string;

/**
 * Map that uses case insensitive regex expression matching for keys
 *
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMap<V> extends LinkedHashMap<NottableString, V> implements Map<NottableString, V> {
    static final long serialVersionUID = 1530623482381786485L;

    public boolean containsAll(CaseInsensitiveRegexHashMap<String> subSet) {
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
        for (NottableString matcherKey : keySet()) {
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
        return super.put(string(key), value);
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        boolean result = false;

        if (key instanceof NottableString) {
            NottableString nottableString = (NottableString) key;
            if (super.containsKey(key)) {
                return true;
            } else {
                for (NottableString keyToCompare : keySet()) {
                    if (RegexStringMatcher.matches(nottableString.getValue(), keyToCompare.getValue(), true)) {
                        result = true;
                        break;
                    }
                }
            }

            result = nottableString.isNot() != result;

        } else if (key instanceof String) {
            result = containsKey(string((String) key));
        }

        return result;
    }

    @Override
    public synchronized V get(Object key) {
        if (key instanceof NottableString) {
            if (super.get(key) != null) {
                return super.get(key);
            } else {
                NottableString nottableString = (NottableString) key;
                for (NottableString keyToCompare : keySet()) {
                    if (nottableString.isNot() != RegexStringMatcher.matches(nottableString.getValue(), keyToCompare.getValue(), true)) {
                        return super.get(keyToCompare);
                    }
                }
            }
        } else if (key instanceof String) {
            return get(string((String) key));
        }
        return null;
    }

    public synchronized Collection<V> getAll(Object key) {
        List<V> values = new ArrayList<V>();
        if (key instanceof NottableString) {
            NottableString nottableString = (NottableString) key;
            for (NottableString keyToCompare : keySet()) {
                if (nottableString.isNot() != RegexStringMatcher.matches(nottableString.getValue(), keyToCompare.getValue(), true)) {
                    values.add(super.get(keyToCompare));
                }
            }
        } else if (key instanceof String) {
            return getAll(string((String) key));
        }
        return values;
    }

    @Override
    public synchronized V remove(Object key) {
        if (key instanceof NottableString) {
            if (super.get(key) != null) {
                return super.remove(key);
            } else {
                NottableString nottableString = (NottableString) key;
                for (NottableString keyToCompare : keySet()) {
                    if (nottableString.isNot() != RegexStringMatcher.matches(nottableString.getValue(), keyToCompare.getValue(), true)) {
                        return super.remove(keyToCompare);
                    }
                }
            }
        } else if (key instanceof String) {
            return remove(string((String) key));
        }
        return null;
    }
}
