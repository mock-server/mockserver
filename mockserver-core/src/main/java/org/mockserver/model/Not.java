package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class Not extends ObjectWithJsonToString {
    private int hashCode;
    Boolean not;

    public static <T extends Not> T not(T t) {
        t.not = true;
        return t;
    }

    public static <T extends Not> T not(T t, Boolean not) {
        if (not != null && not) {
            t.not = true;
        }
        return t;
    }

    @JsonIgnore
    public boolean isNot() {
        return not != null && not;
    }

    public Boolean getNot() {
        return not;
    }

    public void setNot(Boolean not) {
        this.not = not;
        this.hashCode = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        Not not1 = (Not) o;
        return Objects.equals(not, not1.not);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(not);
        }
        return hashCode;
    }
}
