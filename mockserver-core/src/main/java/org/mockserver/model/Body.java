package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public abstract class Body extends EqualsHashCodeToString {

    private final Type type;

    public Body(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        PARAMETERS,
        XPATH,
        JSON,
        REGEX,
        EXACT,
        BINARY
    }
}
