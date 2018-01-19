package org.mockserver.model;

import java.util.Objects;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class KeyAndValue extends ObjectWithJsonToString {
    private final NottableString name;
    private final NottableString value;
    private final int hashCode;

    public KeyAndValue(String name, String value) {
        this(string(name), string(value));
    }

    public KeyAndValue(NottableString name, NottableString value) {
        this.name = name;
        this.value = value;
        this.hashCode = Objects.hash(name, value);
    }

    public NottableString getName() {
        return name;
    }

    public NottableString getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        KeyAndValue that = (KeyAndValue) o;
        return Objects.equals(name, that.name) &&
            Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
