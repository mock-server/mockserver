package org.mockserver.ui.model;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class ValueWithKey {

    private String key;
    private Object value;

    public ValueWithKey(ObjectWithReflectiveEqualsHashCodeToString value) {
        this.value = value;
        this.key = String.valueOf(value.key());
    }

    public ValueWithKey(String value, String key) {
        this.value = value;
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }
}
