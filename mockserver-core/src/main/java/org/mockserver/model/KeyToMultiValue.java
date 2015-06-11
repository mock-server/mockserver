package org.mockserver.model;

import org.mockserver.collections.CaseInsensitiveRegexMultiMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.NottableString.strings;

/**
 * @author jamesdbloom
 */
public class KeyToMultiValue extends ObjectWithJsonToString {
    private final NottableString name;
    private final List<NottableString> values;

    public KeyToMultiValue(String name, String... values) {
        this(string(name), strings(values));
    }

    public KeyToMultiValue(NottableString name, NottableString... values) {
        this(name, Arrays.asList(values));
    }

    public KeyToMultiValue(String name, Collection<String> values) {
        this(string(name), strings(values));
    }

    public KeyToMultiValue(NottableString name, Collection<NottableString> values) {
        this.name = name;
        if (values != null) {
            this.values = new ArrayList<NottableString>(values);
        } else {
            this.values = new ArrayList<NottableString>();
        }
    }

    public static CaseInsensitiveRegexMultiMap toMultiMap(List<? extends KeyToMultiValue> keyToMultiValues) {
        CaseInsensitiveRegexMultiMap caseInsensitiveRegexMultiMap = new CaseInsensitiveRegexMultiMap();
        if (keyToMultiValues != null) {
            for (KeyToMultiValue keyToMultiValue : keyToMultiValues) {
                for (NottableString value : keyToMultiValue.getValues()) {
                    caseInsensitiveRegexMultiMap.put(keyToMultiValue.getName(), value);
                }
            }
        }
        return caseInsensitiveRegexMultiMap;
    }

    public static CaseInsensitiveRegexMultiMap toMultiMap(KeyToMultiValue... keyToMultiValues) {
        return toMultiMap(Arrays.asList(keyToMultiValues));
    }

    public NottableString getName() {
        return name;
    }

    public List<NottableString> getValues() {
        return values;
    }

    public void addValue(String value) {
        if (values != null && !values.contains(string(value))) {
            values.add(string(value));
        }
    }

    public void addValue(NottableString value) {
        if (values != null && !values.contains(value)) {
            values.add(value);
        }
    }

    public void addValues(List<String> values) {
        if (this.values != null) {
            for (String value : values) {
                if (!this.values.contains(string(value))) {
                    this.values.add(string(value));
                }
            }
        }
    }

    public void addNottableValues(List<NottableString> values) {
        if (this.values != null) {
            for (NottableString value : values) {
                if (!this.values.contains(value)) {
                    this.values.add(value);
                }
            }
        }
    }

    public void addValues(String... values) {
        addValues(Arrays.asList(values));
    }

    public void addValues(NottableString... values) {
        addNottableValues(Arrays.asList(values));
    }
}
