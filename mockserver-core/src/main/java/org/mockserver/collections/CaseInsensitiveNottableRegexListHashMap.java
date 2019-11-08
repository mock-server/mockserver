package org.mockserver.collections;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.RegexStringMatcher;
import org.mockserver.model.NottableString;

import java.util.*;

import static org.mockserver.model.NottableString.string;

/**
 * Map that uses case insensitive regex expression matching for keys and values
 *
 * @author jamesdbloom
 */
class CaseInsensitiveNottableRegexListHashMap extends LinkedHashMap<NottableString, List<NottableString>> implements Map<NottableString, List<NottableString>> {

    private final RegexStringMatcher regexStringMatcher;

    CaseInsensitiveNottableRegexListHashMap(MockServerLogger mockServerLogger) {
        regexStringMatcher = new RegexStringMatcher(mockServerLogger);
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        boolean result = false;

        if (key instanceof NottableString) {
            if (super.containsKey(key)) {
                result = true;
            } else {
                for (NottableString keyToCompare : keySet()) {
                    if (regexStringMatcher.matches((NottableString) key, keyToCompare, true)) {
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
    public synchronized List<NottableString> get(Object key) {
        if (key instanceof NottableString) {
            for (Entry<NottableString, List<NottableString>> entry : entrySet()) {
                if (regexStringMatcher.matches((NottableString) key, entry.getKey(), true)) {
                    return entry.getValue();
                }
            }
        } else if (key instanceof String) {
            return get(string((String) key));
        }
        return null;
    }

    public synchronized Collection<List<NottableString>> getAll(Object key) {
        List<List<NottableString>> values = new ArrayList<>();
        if (key instanceof NottableString) {
            for (Entry<NottableString, List<NottableString>> entry : entrySet()) {
                if (regexStringMatcher.matches((NottableString) key, entry.getKey(), true)) {
                    values.add(entry.getValue());
                }
            }
        } else if (key instanceof String) {
            return getAll(string((String) key));
        }
        return values;
    }

    public synchronized List<NottableString> put(String key, List<NottableString> value) {
        return super.put(string(key), value);
    }

    @Override
    public synchronized List<NottableString> remove(Object key) {
        List<NottableString> values = new ArrayList<>();
        if (key instanceof NottableString) {
            for (Entry<NottableString, List<NottableString>> entry : new HashSet<>(entrySet())) {
                if (regexStringMatcher.matches((NottableString) key, entry.getKey(), true)) {
                    values.addAll(super.remove(entry.getKey()));
                }
            }
        } else if (key instanceof String) {
            return remove(string((String) key));
        }
        return values;
    }
}
