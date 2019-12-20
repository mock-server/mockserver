package org.mockserver.model;

import java.nio.charset.Charset;

import static org.mockserver.model.MediaType.DEFAULT_HTTP_CHARACTER_SET;

/**
 * @author jamesdbloom
 */
public class StringBody extends BodyWithContentType<String> {

    private final String value;
    private final byte[] rawBinaryData;
    private final boolean subString;

    public StringBody(String value) {
        this(value, false);
    }

    public StringBody(String value, Charset charset) {
        this(value, false, charset);
    }

    public StringBody(String value, MediaType contentType) {
        this(value, false, contentType);
    }

    public StringBody(String value, boolean subString) {
        this(value, subString, (MediaType) null);
    }

    public StringBody(String value, boolean subString, Charset charset) {
        this(value, subString, (charset != null ? MediaType.create("text", "plain").withCharset(charset) : null));
    }

    public StringBody(String value, boolean subString, MediaType contentType) {
        this(value, null, subString, contentType);
    }

    public StringBody(String value, byte[] rawBinaryData, boolean subString, MediaType contentType) {
        super(Type.STRING, contentType);
        this.value = value;
        this.subString = subString;

        if (rawBinaryData == null && value != null) {
            this.rawBinaryData = value.getBytes(determineCharacterSet(contentType, DEFAULT_HTTP_CHARACTER_SET));
        } else {
            this.rawBinaryData = rawBinaryData;
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

    public static StringBody subString(String body) {
        return new StringBody(body, true);
    }

    public static StringBody subString(String body, Charset charset) {
        return new StringBody(body, true, charset);
    }

    public static StringBody subString(String body, MediaType contentType) {
        return new StringBody(body, true, contentType);
    }

    public String getValue() {
        return value;
    }

    public byte[] getRawBytes() {
        return rawBinaryData;
    }

    public boolean isSubString() {
        return subString;
    }

    @Override
    public String toString() {
        return value;
    }
}
