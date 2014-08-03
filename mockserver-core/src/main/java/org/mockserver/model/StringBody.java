package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class StringBody extends Body<String> {

    private final String value;

    public StringBody(String value) {
        super(Type.STRING);
        this.value = value;
    }

    public StringBody(String value, Type type) {
        super(type);
        this.value = value;
    }

    public static StringBody exact(String body) {
        return new StringBody(body, Type.STRING);
    }

    public static StringBody regex(String body) {
        return new StringBody(body, Type.REGEX);
    }

    public static StringBody xpath(String body) {
        return new StringBody(body, Type.XPATH);
    }

    public static StringBody json(String body) {
        return new StringBody(body, Type.JSON);
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
