package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.charset.Charset;

/**
 * @author jamesdbloom
 */
public abstract class Body<T> extends Not {

    private final Type type;

    public Body(Type type) {
        this.type = type;
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
    public Charset getCharset(Charset defaultIfNotSet) {
        return defaultIfNotSet;
    }

    @JsonIgnore
    public String getContentType() {
        return null;
    }

    public enum Type {
        PARAMETERS,
        XPATH,
        JSON,
        JSON_SCHEMA,
        REGEX,
        STRING,
        BINARY
    }
}
