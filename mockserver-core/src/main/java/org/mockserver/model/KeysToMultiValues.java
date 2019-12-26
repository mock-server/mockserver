package org.mockserver.model;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.ArrayUtils;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.logging.MockServerLogger;

import java.util.*;

import static org.mockserver.model.NottableString.*;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class KeysToMultiValues<T extends KeyToMultiValue, K extends KeysToMultiValues> extends ObjectWithJsonToString {

    private final Multimap<NottableString, NottableString> listMultimap = LinkedHashMultimap.create();
    private final K k = (K) this;

    private CaseInsensitiveRegexMultiMap toCaseInsensitiveRegexMultiMap(MockServerLogger mockServerLogger, final List<T> entries, boolean controlPlaneMatcher) {
        CaseInsensitiveRegexMultiMap caseInsensitiveRegexMultiMap = new CaseInsensitiveRegexMultiMap(mockServerLogger, controlPlaneMatcher);
        if (entries != null) {
            for (KeyToMultiValue keyToMultiValue : entries) {
                for (NottableString value : keyToMultiValue.getValues()) {
                    caseInsensitiveRegexMultiMap.put(keyToMultiValue.getName(), value);
                }
            }
        }
        return caseInsensitiveRegexMultiMap;
    }

    public abstract T build(final NottableString name, final Collection<NottableString> values);

    public K withEntries(final Map<String, List<String>> entries) {
        listMultimap.clear();
        for (String name : entries.keySet()) {
            for (String value : entries.get(name)) {
                withEntry(name, value);
            }
        }
        return k;
    }

    public K withEntries(final List<T> entries) {
        listMultimap.clear();
        if (entries != null) {
            for (T entry : entries) {
                withEntry(entry);
            }
        }
        return k;
    }

    @SafeVarargs
    public final K withEntries(final T... entries) {
        if (ArrayUtils.isNotEmpty(entries)) {
            withEntries(Arrays.asList(entries));
        }
        return k;
    }

    public K withEntry(final T entry) {
        if (entry.getValues().isEmpty()) {
            listMultimap.put(entry.getName(), null);
        } else {
            listMultimap.putAll(entry.getName(), entry.getValues());
        }
        return k;
    }

    public K withEntry(final String name, final String... values) {
        if (values == null || values.length == 0) {
            listMultimap.put(string(name), null);
        } else {
            listMultimap.putAll(string(name), deserializeNottableStrings(values));
        }
        return k;
    }

    public K withEntry(final String name, final List<String> values) {
        if (values == null || values.size() == 0) {
            listMultimap.put(string(name), null);
        } else {
            listMultimap.putAll(string(name), deserializeNottableStrings(values));
        }
        return k;
    }

    public K withEntry(final NottableString name, final List<NottableString> values) {
        if (values != null) {
            listMultimap.putAll(name, values);
        }
        return k;
    }

    public K withEntry(final NottableString name, final NottableString... values) {
        if (ArrayUtils.isNotEmpty(values)) {
            withEntry(name, Arrays.asList(values));
        }
        return k;
    }

    public K remove(final String name) {
        for (NottableString key : listMultimap.keySet().toArray(new NottableString[0])) {
            if (key.equalsIgnoreCase(name)) {
                listMultimap.removeAll(key);
            }
        }
        return k;
    }

    public K remove(final NottableString name) {
        for (NottableString key : listMultimap.keySet().toArray(new NottableString[0])) {
            if (key.equalsIgnoreCase(name)) {
                listMultimap.removeAll(key);
            }
        }
        return k;
    }

    @SuppressWarnings("UnusedReturnValue")
    K replaceEntry(final T entry) {
        if (entry != null) {
            remove(entry.getName());
            listMultimap.putAll(entry.getName(), entry.getValues());
        }
        return k;
    }

    @SuppressWarnings("UnusedReturnValue")
    K replaceEntry(final String name, final String... values) {
        if (ArrayUtils.isNotEmpty(values)) {
            remove(name);
            listMultimap.putAll(string(name), deserializeNottableStrings(values));
        }
        return k;
    }

    public List<T> getEntries() {
        if (!isEmpty()) {
            ArrayList<T> headers = new ArrayList<>();
            for (NottableString nottableString : listMultimap.keySet().toArray(new NottableString[0])) {
                headers.add(build(nottableString, listMultimap.get(nottableString)));
            }
            return headers;
        } else {
            return Collections.emptyList();
        }
    }

    public Set<NottableString> keySet() {
        return listMultimap.keySet();
    }

    public Collection<NottableString> getValues(NottableString key) {
        return listMultimap.get(key);
    }

    public Multimap<NottableString, NottableString> getMultimap() {
        return listMultimap;
    }

    public List<String> getValues(final String name) {
        if (!isEmpty() && name != null) {
            List<String> values = new ArrayList<>();
            for (NottableString key : listMultimap.keySet().toArray(new NottableString[0])) {
                if (key != null && key.equalsIgnoreCase(name)) {
                    values.addAll(serialiseNottableString(listMultimap.get(key)));
                }
            }
            return values;
        } else {
            return Collections.emptyList();
        }
    }

    String getFirstValue(final String name) {
        if (!isEmpty()) {
            for (NottableString key : listMultimap.keySet().toArray(new NottableString[0])) {
                if (key != null && key.equalsIgnoreCase(name)) {
                    Collection<NottableString> nottableStrings = listMultimap.get(key);
                    if (!nottableStrings.isEmpty()) {
                        NottableString next = nottableStrings.iterator().next();
                        if (next != null) {
                            return next.getValue();
                        }
                    }
                }
            }
        }
        return "";
    }

    public boolean containsEntry(final String name) {
        if (!isEmpty()) {
            for (NottableString key : listMultimap.keySet().toArray(new NottableString[0])) {
                if (key != null && key.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean containsEntry(final String name, final String value) {
        return containsEntry(string(name), string(value));
    }

    boolean containsEntry(final NottableString name, final NottableString value) {
        if (!isEmpty() && name != null && value != null) {
            for (NottableString entryKey : listMultimap.keySet().toArray(new NottableString[0])) {
                if (entryKey != null && entryKey.equalsIgnoreCase(name)) {
                    Collection<NottableString> nottableStrings = listMultimap.get(entryKey);
                    if (nottableStrings != null) {
                        for (NottableString entryValue : nottableStrings.toArray(new NottableString[0])) {
                            if (value.equalsIgnoreCase(entryValue)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public CaseInsensitiveRegexMultiMap toCaseInsensitiveRegexMultiMap(MockServerLogger mockServerLogger, boolean controlPlaneMatcher) {
        return toCaseInsensitiveRegexMultiMap(mockServerLogger, this.getEntries(), controlPlaneMatcher);
    }

    public boolean isEmpty() {
        return listMultimap.isEmpty();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KeysToMultiValues)) {
            return false;
        }
        KeysToMultiValues<?, ?> that = (KeysToMultiValues<?, ?>) o;
        return Objects.equals(listMultimap, that.listMultimap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listMultimap);
    }
}
