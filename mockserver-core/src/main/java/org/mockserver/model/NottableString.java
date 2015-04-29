package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class NottableString extends Not {

    private final String value;

    private NottableString(String value, Boolean not) {
        this.value = value;
        this.not = not;
    }

    public static NottableString string(String value, Boolean not) {
        return new NottableString(value, not);
    }

    public static NottableString string(String value) {
        return new NottableString(value, null);
    }

    public static NottableString not(String value) {
        return new NottableString(value, Boolean.TRUE);
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
            return isNot() != (value.equals(other));
        } else if (other instanceof NottableString) {
            NottableString otherNottableString = (NottableString) other;
            return otherNottableString.isNot() == (isNot() == (value.equals(otherNottableString.getValue())));
        }
        return false;
    }
}
