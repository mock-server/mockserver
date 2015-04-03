package org.mockserver.model;

import org.mockserver.client.serialization.Base64Converter;

/**
 * @author jamesdbloom
 */
public class BinaryBody extends Body<byte[]> {

    private final byte[] bytes;

    public BinaryBody(byte[] bytes) {
        super(Type.BINARY);
        this.bytes = bytes;
    }

    public static BinaryBody binary(byte[] body) {
        return new BinaryBody(body);
    }

    public byte[] getValue() {
        return bytes;
    }

    public byte[] getRawBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return bytes != null ? Base64Converter.stringToBase64Bytes(bytes) : null;
    }
}
