package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author jamesdbloom
 */
public abstract class Action extends EqualsHashCodeToString {

    @JsonIgnore
    public abstract Type getType();

    public enum Type {
        FORWARD,
        RESPONSE,
        CALLBACK
    }
}
