package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class StringBody extends Body<String> {

    private final String value;
    private final byte[] rawBinaryData;

    public StringBody(String value, byte[] rawBinaryData) {
        super(Type.STRING);
        this.value = value;
        this.rawBinaryData = rawBinaryData;
    }

    public StringBody(String value, Type type) {
        super(type);
        this.value = value;
        if (value != null) {
            this.rawBinaryData = value.getBytes();
        } else {
            this.rawBinaryData = new byte[0];
        }
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

    public byte[] getRawBytes() {
        return rawBinaryData;
    }

    @Override
    public String toString() {
        return value;
    }
}
