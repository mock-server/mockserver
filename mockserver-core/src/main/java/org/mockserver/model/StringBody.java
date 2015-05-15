package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.net.MediaType;

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
        this.charset = charset;

        if (value != null) {
            this.rawBinaryData = value.getBytes(charset != null ? charset : DEFAULT_HTTP_CHARACTER_SET);
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

    public String getValue() {
        return value;
    }

    public byte[] getRawBytes() {
        return rawBinaryData;
    }

    public Charset getCharset() {
        return charset;
    }

    @JsonIgnore
    public Charset getCharset(Charset defaultIfNotSet) {
        return charset != null ? charset : defaultIfNotSet;
    }

    @JsonIgnore
    public String getContentType() {
        return MediaType.create("text", "plain").toString();
    }

    @Override
    public String toString() {
        return value;
    }
}
