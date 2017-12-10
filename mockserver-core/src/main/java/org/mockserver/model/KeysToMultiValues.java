package org.mockserver.model;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import org.apache.commons.lang3.ArrayUtils;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockserver.model.NottableString.*;

/**
 * @author jamesdbloom
 */
public abstract class KeysToMultiValues<T extends KeyToMultiValue, K extends KeysToMultiValues> extends ObjectWithJsonToString {

    private ListMultimap<NottableString, NottableString> listMultimap = LinkedListMultimap.create();

    @SafeVarargs
    public static <T extends KeyToMultiValue> CaseInsensitiveRegexMultiMap toCaseInsensitiveRegexMultiMap(T... entries) {
        return toCaseInsensitiveRegexMultiMap(Arrays.asList(entries));
    }

    public static <T extends KeyToMultiValue> CaseInsensitiveRegexMultiMap toCaseInsensitiveRegexMultiMap(List<T> entries) {
        CaseInsensitiveRegexMultiMap caseInsensitiveRegexMultiMap = new CaseInsensitiveRegexMultiMap();
        if (entries != null) {
            for (KeyToMultiValue keyToMultiValue : entries) {
                for (NottableString value : keyToMultiValue.getValues()) {
                    caseInsensitiveRegexMultiMap.put(keyToMultiValue.getName(), value);
                }
            }
        }
        return caseInsensitiveRegexMultiMap;
    }

    public abstract T build(NottableString name, List<NottableString> values);

    public K withEntries(Map<String, List<String>> entries) {
        this.listMultimap.clear();
        for (String name : entries.keySet()) {
            for (String value : entries.get(name)) {
                withEntry(name, value);
            }
        }
        return (K) this;
    }

    public K withEntries(List<T> entries) {
        this.listMultimap.clear();
        if (entries != null) {
            for (T entry : entries) {
                withEntry(entry);
            }
        }
        return (K) this;
    }

    @SafeVarargs
    public final K withEntries(T... entries) {
        if (ArrayUtils.isNotEmpty(entries)) {
            withEntries(Arrays.asList(entries));
        }
        return (K) this;
    }

    public K withEntry(T entry) {
        if (entry.getValues().isEmpty()) {
            this.listMultimap.put(entry.getName(), null);
        } else {
            this.listMultimap.putAll(entry.getName(), entry.getValues());
        }
        return (K) this;
    }

    public K withEntry(String name, String... values) {
        if (ArrayUtils.isNotEmpty(values)) {
            this.listMultimap.putAll(string(name), deserializeNottableStrings(values));
        }
        return (K) this;
    }

    public K withEntry(NottableString name, List<NottableString> values) {
        if (values != null) {
            this.listMultimap.putAll(name, values);
        }
        return (K) this;
    }

    public K withEntry(NottableString name, NottableString... values) {
        if (ArrayUtils.isNotEmpty(values)) {
            withEntry(name, Arrays.asList(values));
        }
        return (K) this;
    }

    public K replaceEntry(T entry) {
        if (entry != null) {
            this.listMultimap.replaceValues(entry.getName(), entry.getValues());
        }
        return (K) this;
    }

    public K replaceEntry(String name, String... values) {
        if (ArrayUtils.isNotEmpty(values)) {
            this.listMultimap.replaceValues(string(name), deserializeNottableStrings(values));
        }
        return (K) this;
    }

    public List<T> getEntries() {
        ArrayList<T> headers = new ArrayList<>();
        for (NottableString nottableString : this.listMultimap.asMap().keySet()) {
            headers.add(build(nottableString, this.listMultimap.get(nottableString)));
        }
        return headers;
    }

    public List<String> getValues(String name) {
        List<String> values = new ArrayList<>();
        for (NottableString key : listMultimap.keySet()) {
            if (key != null &&
                name != null &&
                key.equalsIgnoreCase(name)) {
                values.addAll(serialiseNottableString(listMultimap.get(key)));
            }
        }
        return values;
    }

    public String getFirstValue(String name) {
        String firstEntryValue = "";
        List<String> values = getValues(name);
        if (!values.isEmpty()) {
            firstEntryValue = values.get(0);
        }
        return firstEntryValue;
    }

    public boolean containsEntry(String name) {
        if (!getValues(name).isEmpty()) {
            return true;
        }
        return false;
    }

    public boolean containsEntry(String name, String value) {
        return containsEntry(string(name), string(value));
    }

    public boolean containsEntry(NottableString name, NottableString value) {
        for (Map.Entry<NottableString, NottableString> entry : listMultimap.entries()) {
            if (entry.getKey() != null &&
                name != null &&
                entry.getKey().equalsIgnoreCase(name)) {
                if (entry.getValue() != null &&
                    value != null &&
                    value.equalsIgnoreCase(entry.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    public CaseInsensitiveRegexMultiMap toCaseInsensitiveRegexMultiMap() {
        return toCaseInsensitiveRegexMultiMap(this.getEntries());
    }

    public boolean isEmpty() {
        return listMultimap.isEmpty();
    }
}
