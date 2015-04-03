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

    public StringBody(String value) {
        super(Type.STRING);
        this.value = value;
        if (value != null) {
            this.rawBinaryData = value.getBytes();
        } else {
            this.rawBinaryData = new byte[0];
        }
    }

    public static StringBody exact(String body) {
        return new StringBody(body);
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
