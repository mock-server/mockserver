package org.mockserver.collections;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.logging.MockServerLogger;
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

    private final RegexStringMatcher regexStringMatcher;

    public CaseInsensitiveRegexHashMap(MockServerLogger mockServerLogger) {
        regexStringMatcher = new RegexStringMatcher(mockServerLogger);
    }

    @VisibleForTesting
    public static CaseInsensitiveRegexHashMap hashMap(String[]... keyAndValues) {
        CaseInsensitiveRegexHashMap hashMap = new CaseInsensitiveRegexHashMap(new MockServerLogger());
        for (String[] keyAndValue : keyAndValues) {
            if (keyAndValue.length >= 2) {
                hashMap.put(keyAndValue[0], keyAndValue[1]);
            }
        }
        return hashMap;
    }

    @VisibleForTesting
    public static CaseInsensitiveRegexHashMap hashMap(NottableString[]... keyAndValues) {
        CaseInsensitiveRegexHashMap hashMap = new CaseInsensitiveRegexHashMap(new MockServerLogger());
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
            if (regexStringMatcher.matches(value, matcherEntry.getValue(), true)
                && regexStringMatcher.matches(key, matcherEntry.getKey(), true)) {
                result = true;
                break;
            }
        }

        return result;
    }

    private synchronized Entry<NottableString, NottableString> retrieveEntry(NottableString key, NottableString value) {
        for (Entry<NottableString, NottableString> matcherEntry : entrySet()) {
            if (regexStringMatcher.matches(value, matcherEntry.getValue(), true)
                && regexStringMatcher.matches(key, matcherEntry.getKey(), true)) {
                return matcherEntry;
            }
        }
        return null;
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        boolean result = false;

        if (key instanceof NottableString) {
            for (NottableString keyToCompare : keySet()) {
                if (regexStringMatcher.matches(((NottableString) key), keyToCompare, true)) {
                    result = true;
                    break;
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
                if (regexStringMatcher.matches((NottableString) value, entry.getValue(), true)) {
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
                if (regexStringMatcher.matches((NottableString) key, entry.getKey(), true)) {
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
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        return super.put(key, value);
    }

    @Override
    public synchronized NottableString remove(Object key) {
        if (key instanceof NottableString) {
            for (Entry<NottableString, NottableString> entry : entrySet()) {
                if (regexStringMatcher.matches((NottableString) key, entry.getKey(), true)) {
                    return super.remove(entry.getKey());
                }
            }
        } else if (key instanceof String) {
            return remove(string((String) key));
        }
        return null;
    }
}
