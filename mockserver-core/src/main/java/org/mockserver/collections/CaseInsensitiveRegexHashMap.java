package org.mockserver.collections;

import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.NottableString;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockserver.model.NottableString.string;

/**
 * Map that uses case insensitive regex expression matching for keys and values
 *
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMap extends LinkedHashMap<NottableString, NottableString> implements Map<NottableString, NottableString> {

    public static CaseInsensitiveRegexHashMap hashMap(String[]... keyAndValues) {
        CaseInsensitiveRegexHashMap hashMap = new CaseInsensitiveRegexHashMap();
        for (String[] keyAndValue : keyAndValues) {
            if (keyAndValue.length >= 2) {
                hashMap.put(keyAndValue[0], keyAndValue[1]);
            }
        }
        return hashMap;
    }

    public static CaseInsensitiveRegexHashMap hashMap(NottableString[]... keyAndValues) {
        CaseInsensitiveRegexHashMap hashMap = new CaseInsensitiveRegexHashMap();
        for (NottableString[] keyAndValue : keyAndValues) {
            if (keyAndValue.length >= 2) {
                hashMap.put(keyAndValue[0], keyAndValue[1]);
            }
        }
        return hashMap;
    }

    public boolean containsAll(CaseInsensitiveRegexHashMap subSet) {
        if (size() == 0 && subSet.allKeysNotted()) {
            return true;
        } else {
            for (Entry<NottableString, NottableString> entry : subSet.entrySet()) {
                if (entry.getKey().isNot() && entry.getValue().isNot() && containsKeyValue(entry.getKey().getValue(), entry.getValue().getValue())) {
                    return false;
                } else if (entry.getKey().isNot() && containsKey(entry.getKey().getValue())) {
                    return false;
                } else if (!containsKeyValue(entry.getKey(), entry.getValue())) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean allKeysNotted() {
        for (NottableString key : keySet()) {
            if (!key.isNot()) {
                return false;
            }
        }
        return true;
    }

    public synchronized boolean containsKeyValue(String key, String value) {
        return containsKeyValue(string(key), string(value));
    }

    public synchronized boolean containsKeyValue(NottableString key, NottableString value) {
        boolean result = false;

        for (Entry<NottableString, NottableString> matcherEntry : entrySet()) {
            if (RegexStringMatcher.matches(value, matcherEntry.getValue(), true)
                    && RegexStringMatcher.matches(key, matcherEntry.getKey(), true)) {
                result = true;
                break;
            }
        }

        return result;
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        boolean result = false;

        if (key instanceof NottableString) {
            if (super.containsKey(key)) {
                result = true;
            } else {
                for (NottableString keyToCompare : keySet()) {
                    if (RegexStringMatcher.matches(((NottableString) key), keyToCompare, true)) {
                        result = true;
                        break;
                    }
                }
            }
        } else if (key instanceof String) {
            result = containsKey(string((String) key));
        }

        return result;
    }

    @Override
    public synchronized boolean containsValue(Object value) {
        boolean result = false;

        if (value instanceof NottableString) {
            for (Entry<NottableString, NottableString> entry : entrySet()) {
                if (RegexStringMatcher.matches((NottableString) value, entry.getValue(), true)) {
                    return true;
                }
            }
        } else if (value instanceof String) {
            result = containsValue(string((String) value));
        }

        return result;
    }

    @Override
    public synchronized NottableString get(Object key) {
        if (key instanceof NottableString) {
            for (Entry<NottableString, NottableString> entry : entrySet()) {
                if (RegexStringMatcher.matches((NottableString) key, entry.getKey(), true)) {
                    return super.get(entry.getKey());
                }
            }
        } else if (key instanceof String) {
            return get(string((String) key));
        }
        return null;
    }

    public synchronized NottableString put(String key, String value) {
        return super.put(string(key), string(value));
    }

    public synchronized NottableString put(NottableString key, NottableString value) {
        return super.put(key, value);
    }

    @Override
    public synchronized NottableString remove(Object key) {
        if (key instanceof NottableString) {
            for (Entry<NottableString, NottableString> entry : entrySet()) {
                if (RegexStringMatcher.matches((NottableString) key, entry.getKey(), true)) {
                    return super.remove(entry.getKey());
                }
            }
        } else if (key instanceof String) {
            return remove(string((String) key));
        }
        return null;
    }
}
