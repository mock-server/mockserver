package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
