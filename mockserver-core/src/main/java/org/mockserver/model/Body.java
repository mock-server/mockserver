package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.net.MediaType;

import java.nio.charset.Charset;

/**
 * @author jamesdbloom
 */
public abstract class Body<T> extends Not {

    private final Type type;
    protected final MediaType contentType;

    public Body(Type type, MediaType contentType) {
        this.type = type;
        this.contentType = contentType;
    }

    public Type getType() {
        return type;
    }

    public abstract T getValue();

    @JsonIgnore
    public byte[] getRawBytes() {
        return toString().getBytes();
    }

    @JsonIgnore
    Charset determineCharacterSet(MediaType contentType, Charset defaultCharset) {
        if (contentType != null && contentType.charset().isPresent()) {
            return contentType.charset().get();
        }
        return defaultCharset;
    }

    @JsonIgnore
    public Charset getCharset(Charset defaultIfNotSet) {
        return determineCharacterSet(contentType, defaultIfNotSet);
    }

    public String getContentType() {
        return (contentType != null ? contentType.toString() : null);
    }

    public enum Type {
        BINARY,
        JSON,
        JSON_SCHEMA,
        PARAMETERS,
        REGEX,
        STRING,
        XML,
        XML_SCHEMA,
        XPATH,
    }
}
