package org.mockserver.model;

import org.mockserver.collections.CaseInsensitiveRegexHashMap;

import java.util.Arrays;
import java.util.List;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class KeyAndValue extends ObjectWithJsonToString {
    private final NottableString name;
    private final NottableString value;

    public KeyAndValue(String name, String value) {
        this(string(name), string(value));
    }

    public KeyAndValue(NottableString name, NottableString value) {
        this.name = name;
        this.value = value;
    }

    public static CaseInsensitiveRegexHashMap toHashMap(List<? extends KeyAndValue> keyAndValue) {
        CaseInsensitiveRegexHashMap caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap();
        if (keyAndValue != null) {
            for (KeyAndValue keyToMultiValue : keyAndValue) {
                caseInsensitiveRegexHashMap.put(keyToMultiValue.getName(), keyToMultiValue.getValue());
            }
        }
        return caseInsensitiveRegexHashMap;
    }

    public static CaseInsensitiveRegexHashMap toHashMap(KeyAndValue... keyToMultiValues) {
        return toHashMap(Arrays.asList(keyToMultiValues));
    }

    public NottableString getName() {
        return name;
    }

    public NottableString getValue() {
        return value;
    }

}
