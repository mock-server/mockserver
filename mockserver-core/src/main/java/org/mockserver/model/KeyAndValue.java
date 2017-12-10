package org.mockserver.model;

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

    public NottableString getName() {
        return name;
    }

    public NottableString getValue() {
        return value;
    }

}
