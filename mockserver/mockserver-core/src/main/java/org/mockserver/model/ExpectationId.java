package org.mockserver.model;

import java.util.Objects;

public class ExpectationId extends ObjectWithJsonToString {

    private int hashCode;
    private String id;

    public static ExpectationId expectationId(String id) {
        return new ExpectationId().withId(id);
    }

    public String getId() {
        return id;
    }

    public ExpectationId withId(String id) {
        this.id = id;
        return this;
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
        ExpectationId that = (ExpectationId) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(id);
        }
        return hashCode;
    }

}
