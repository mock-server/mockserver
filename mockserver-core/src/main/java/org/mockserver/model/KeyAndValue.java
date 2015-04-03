package org.mockserver.model;

import org.mockserver.collections.CaseInsensitiveRegexHashMap;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class KeyAndValue extends ObjectWithReflectiveEqualsHashCodeToString {
    private final String name;
    private final String value;

    public KeyAndValue(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public static CaseInsensitiveRegexHashMap toHashMap(List<? extends KeyAndValue> keyAndValue) {
        CaseInsensitiveRegexHashMap<String> caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap<String>();
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

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

}
