package org.mockserver.model;

import org.mockserver.collections.NottableStringHashMap;
import org.mockserver.logging.MockServerLogger;

import java.util.*;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class KeysAndValues<T extends KeyAndValue, K extends KeysAndValues> extends ObjectWithJsonToString {

    private final Map<NottableString, NottableString> map;

    protected KeysAndValues() {
        map = new LinkedHashMap<>();
    }

    protected KeysAndValues(Map<NottableString, NottableString> map) {
        this.map = new LinkedHashMap<>(map);
    }

    public abstract T build(NottableString name, NottableString value);

    public K withEntries(List<T> cookies) {
        map.clear();
        if (cookies != null) {
            for (T cookie : cookies) {
                withEntry(cookie);
            }
        }
        return (K) this;
    }

    public K withEntries(T... cookies) {
        if (cookies != null) {
            withEntries(Arrays.asList(cookies));
        }
        return (K) this;
    }

    public K withEntry(T cookie) {
        map.put(cookie.getName(), cookie.getValue());
        return (K) this;
    }

    public K withEntry(String name, String value) {
        map.put(string(name), string(value));
        return (K) this;
    }

    public K withEntry(NottableString name, NottableString value) {
        map.put(name, value);
        return (K) this;
    }

    public List<T> getEntries() {
        if (!map.isEmpty()) {
            ArrayList<T> cookies = new ArrayList<>();
            for (NottableString nottableString : map.keySet()) {
                cookies.add(build(nottableString, map.get(nottableString)));
            }
            return cookies;
        } else {
            return Collections.emptyList();
        }
    }

    public Map<NottableString, NottableString> getMap() {
        return map;
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }
}
