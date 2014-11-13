package org.mockserver.model;

import org.mockserver.client.serialization.Base64Converter;

/**
 * @author jamesdbloom
 */
public class BinaryBody extends Body<byte[]> {

    private final byte[] value;

    public BinaryBody(byte[] value) {
        super(Type.BINARY);
        this.value = value;
    }

    public static BinaryBody binary(byte[] body) {
        return new BinaryBody(body);
    }

    public byte[] getValue() {
        return value;
    }

    public byte[] getRawBytes() {
        return value;
    }

    @Override
    public String toString() {
        return value != null ? Base64Converter.stringToBase64Bytes(value) : null;
    }
}
