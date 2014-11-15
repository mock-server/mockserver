package org.mockserver.model;

import org.mockserver.collections.CaseInsensitiveRegexMultiMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class KeyToMultiValue extends ObjectWithReflectiveEqualsHashCodeToString {
    private final String name;
    private final List<String> values;

    public KeyToMultiValue(String name, String... values) {
        this(name, Arrays.asList(values));
    }

    public KeyToMultiValue(String name, Collection<String> values) {
        this.name = name;
        if (values != null) {
            this.values = new ArrayList<String>(values);
        } else {
            this.values = new ArrayList<String>();
        }
    }

    public static CaseInsensitiveRegexMultiMap toMultiMap(List<? extends KeyToMultiValue> keyToMultiValues) {
        CaseInsensitiveRegexMultiMap caseInsensitiveRegexMultiMap = new CaseInsensitiveRegexMultiMap();
        if (keyToMultiValues != null) {
            for (KeyToMultiValue keyToMultiValue : keyToMultiValues) {
                for (String value : keyToMultiValue.getValues()) {
                    caseInsensitiveRegexMultiMap.put(keyToMultiValue.getName(), value);
                }
            }
        }
        return caseInsensitiveRegexMultiMap;
    }

    public static CaseInsensitiveRegexMultiMap toMultiMap(KeyToMultiValue... keyToMultiValues) {
        return toMultiMap(Arrays.asList(keyToMultiValues));
    }

    public String getName() {
        return name;
    }

    public List<String> getValues() {
        return values;
    }

    public void addValue(String value) {
        if (values != null && !values.contains(value)) {
            values.add(value);
        }
    }

    public void addValues(List<String> values) {
        if (this.values != null) {
            for (String value : values) {
                if (!this.values.contains(value)) {
                    this.values.add(value);
                }
            }
        }
    }
}
