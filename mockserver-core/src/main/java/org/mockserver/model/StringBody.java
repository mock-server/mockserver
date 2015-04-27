package org.mockserver.model;

import org.mockserver.mappers.ContentTypeMapper;

import java.nio.charset.Charset;

/**
 * @author jamesdbloom
 */
public class StringBody extends Body<String> {

    private final String value;
    private final byte[] rawBinaryData;

    /**
     * When present, the contents of this StringBody will be forced to the specified character set.
     */
    private final Charset charset;

    /**
     * @deprecated use {@link #StringBody(String, Charset)}
     */
    @Deprecated
    public StringBody(String value, byte[] rawBinaryData) {
        super(Type.STRING);
        this.value = value;
        this.rawBinaryData = rawBinaryData;
        charset = null;
    }

    /**
     * Creates a StringBody that will store the specified value in the {@link ContentTypeMapper#DEFAULT_HTTP_CHARACTER_SET}.
     * Use {@link #StringBody(String, Charset)} to explicitly set the character set of the encoded string.
     *
     * @param value body contents
     */
    public StringBody(String value) {
        super(Type.STRING);
        this.value = value;

        if (value != null) {
            this.rawBinaryData = value.getBytes(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET);
        } else {
            this.rawBinaryData = new byte[0];
        }

        charset = null;
    }

    /**
     * Creates a StringBody that will store the specified value in the specified charset.
     *
     * @param value body contents
     * @param charset character set to encode the contents in when generating the body byte[] contents
     */
    public StringBody(String value, Charset charset) {
        super(Type.STRING);
        this.value = value;

        if (value != null) {
            this.rawBinaryData = value.getBytes(charset);
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

    /**
     * Returns the charset of this StringBody, if it was explicitly set.
     *
     * @return charset associated with this StringBody, or null if one was not specified
     */
    public Charset getCharset() {
        return charset;
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
