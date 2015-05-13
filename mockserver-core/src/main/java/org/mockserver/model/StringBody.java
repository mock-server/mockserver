package org.mockserver.model;

import java.nio.charset.Charset;

import static org.mockserver.mappers.ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET;

/**
 * @author jamesdbloom
 */
public class StringBody extends Body<String> {

    private final String value;
    private final byte[] rawBinaryData;
    private final Charset charset;

    public StringBody(String value) {
        this(value, null);
    }

    public StringBody(String value, Charset charset) {
        super(Type.STRING);
        this.value = value;

        if (value != null) {
            this.rawBinaryData = value.getBytes(charset != null ? charset : DEFAULT_HTTP_CHARACTER_SET);
        } else {
            this.rawBinaryData = new byte[0];
        }

        this.charset = charset;
    }

    public static StringBody exact(String body) {
        return new StringBody(body);
    }

    public static StringBody exact(String body, Charset charset) {
        return new StringBody(body, charset);
    }

    public String getValue() {
        return value;
    }

    public byte[] getRawBytes() {
        return rawBinaryData;
    }

    public Charset getCharset() {
        return charset;
    }

    @Override
    public String toString() {
        return value;
    }
}
