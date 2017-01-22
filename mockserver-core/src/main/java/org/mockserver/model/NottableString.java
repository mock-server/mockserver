package org.mockserver.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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

    public static List<NottableString> strings(String... values) {
        return strings(Arrays.asList(values));
    }

    public static List<NottableString> strings(Collection<String> values) {
        List<NottableString> nottableValues = new ArrayList<NottableString>();
        if (values != null && !values.isEmpty()) {
            for (String value : values) {
                nottableValues.add(string(value));
            }
        }
        return nottableValues;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return equals(other, false);
    }

    public boolean equalsIgnoreCase(Object other) {
        return equals(other, true);
    }

    private boolean equals(Object other, boolean ignoreCase) {
        if (other instanceof String) {
            if (ignoreCase) {
                return isNot() != ((String) other).equalsIgnoreCase(value);
            } else {
                return isNot() != other.equals(value);
            }
        } else if (other instanceof NottableString) {
            NottableString otherNottableString = (NottableString) other;
            if (otherNottableString.getValue() == null) {
                return value == null;
            }
            if (ignoreCase) {
                return otherNottableString.isNot() == (isNot() == otherNottableString.getValue().equalsIgnoreCase(value));
            } else {
                return otherNottableString.isNot() == (isNot() == otherNottableString.getValue().equals(value));
            }
        }
        return false;
    }
}
