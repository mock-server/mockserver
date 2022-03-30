package org.mockserver.model;

import java.util.*;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
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

    public K withEntries(List<T> entries) {
        map.clear();
        if (entries != null) {
            for (T cookie : entries) {
                withEntry(cookie);
            }
        }
        return (K) this;
    }

    public K withEntries(T... entries) {
        if (entries != null) {
            withEntries(Arrays.asList(entries));
        }
        return (K) this;
    }

    public K withEntry(T entry) {
        if (entry != null) {
            map.put(entry.getName(), entry.getValue());
        }
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

    public K replaceEntryIfExists(final T entry) {
        if (entry != null) {
            if (remove(entry.getName())) {
                map.put(entry.getName(), entry.getValue());
            }
        }
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

    public boolean remove(NottableString name) {
        return remove(name.getValue());
    }

    public boolean remove(String name) {
        if (isNotBlank(name)) {
            return map.remove(string(name)) != null;
        }
        return false;
    }

    public abstract K clone();
}
