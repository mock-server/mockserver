package org.mockserver.collections;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

/**
 * @author jamesdbloom
 */
public class NottableKey {

    private final boolean not;
    private final String value;

    private NottableKey(String value, Boolean not) {
        this.value = value;
        this.not = not != null && not;
    }

    public static NottableKey nottableKey(String value, Boolean not) {
        return new NottableKey(value, not);
    }

    public static NottableKey nottableKey(String value) {
        return new NottableKey(value, Boolean.FALSE);
    }

    public boolean isNot() {
        return not;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return value != null;
        } else if (value == null) {
            return false;
        }
        if (other instanceof String) {
            return not != (value.equals(other));
        } else if (other instanceof NottableKey) {
            return not != (value.equals(((NottableKey) other).getValue()));
        }
        return false;
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toStringExclude(this);
    }
}
