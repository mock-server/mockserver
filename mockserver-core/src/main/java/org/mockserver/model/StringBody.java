package org.mockserver.model;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class StringBody extends Body {

    private final String value;

    public static StringBody exact(String body) {
        return new StringBody(body, Type.EXACT);
    }

    public static StringBody regex(String body) {
        return new StringBody(body, Type.REGEX);
    }

    public static StringBody xpath(String body) {
        return new StringBody(body, Type.XPATH);
    }

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
