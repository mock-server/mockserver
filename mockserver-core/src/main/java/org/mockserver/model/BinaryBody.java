package org.mockserver.model;

import javax.xml.bind.DatatypeConverter;

/**
 * @author jamesdbloom
 */
public class BinaryBody extends Body {

    private final byte[] value;

    public static BinaryBody binary(byte[] body) {
        return new BinaryBody(body);
    }

    public BinaryBody(byte[] value) {
        super(Type.BINARY);
        this.value = value;
    }

    public byte[] getValue() {
        return value;
    }

    @Override
    public String toString() {
        return new String(value);
    }
}
