package org.mockserver.model;

import com.google.common.net.MediaType;

import java.nio.charset.Charset;

import static org.mockserver.mappers.ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET;

/**
 * @author jamesdbloom
 */
public class StringBody extends BodyWithContentType<String> {

    private final String value;
    private final byte[] rawBinaryData;

    public StringBody(String value) {
        this(value, (MediaType) null);
    }

    public StringBody(String value, Charset charset) {
        this(value, (charset != null ? MediaType.create("text", "plain").withCharset(charset) : null));
    }

    public StringBody(String value, MediaType contentType) {
        super(Type.STRING, contentType);
        this.value = value;

        if (value != null) {
            this.rawBinaryData = value.getBytes(determineCharacterSet(contentType, DEFAULT_HTTP_CHARACTER_SET));
        } else {
            this.rawBinaryData = new byte[0];
        }
    }

    public static StringBody exact(String body) {
        return new StringBody(body);
    }

    public static StringBody exact(String body, Charset charset) {
        return new StringBody(body, charset);
    }

    public static StringBody exact(String body, MediaType contentType) {
        return new StringBody(body, contentType);
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
