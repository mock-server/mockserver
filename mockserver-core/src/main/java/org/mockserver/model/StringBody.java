package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class StringBody extends Body {

    private final String value;

    public StringBody(String value, Type type) {
        super(type);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
