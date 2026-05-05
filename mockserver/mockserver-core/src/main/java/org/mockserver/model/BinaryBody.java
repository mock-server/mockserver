package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.serialization.Base64Converter;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class BinaryBody extends BodyWithContentType<byte[]> {
    private int hashCode;
    private final byte[] bytes;
    private final Base64Converter base64Converter = new Base64Converter();

    public BinaryBody(byte[] bytes) {
        this(bytes, null);
    }

    public BinaryBody(byte[] bytes, MediaType contentType) {
        super(Type.BINARY, contentType);
        this.bytes = bytes;
    }

    public static BinaryBody binary(byte[] body) {
        return new BinaryBody(body);
    }

    public static BinaryBody binary(byte[] body, MediaType contentType) {
        return new BinaryBody(body, contentType);
    }

    public byte[] getValue() {
        return bytes;
    }

    @JsonIgnore
    public byte[] getRawBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return bytes != null ? base64Converter.bytesToBase64String(bytes) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        BinaryBody that = (BinaryBody) o;
        return Arrays.equals(bytes, that.bytes) &&
            Objects.equals(base64Converter, that.base64Converter);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            int result = Objects.hash(super.hashCode(), base64Converter);
            hashCode = 31 * result + Arrays.hashCode(bytes);
        }
        return hashCode;
    }
}
