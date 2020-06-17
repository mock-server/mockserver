package org.mockserver.model;

import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class JsonPathBody extends Body<String> {
    private int hashCode;
    private final String jsonPath;

    public JsonPathBody(String jsonPath) {
        super(Type.JSON_PATH);
        this.jsonPath = jsonPath;
    }

    public static JsonPathBody jsonPath(String jsonPath) {
        return new JsonPathBody(jsonPath);
    }

    public String getValue() {
        return jsonPath;
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
        if (!super.equals(o)) {
            return false;
        }
        JsonPathBody that = (JsonPathBody) o;
        return Objects.equals(jsonPath, that.jsonPath);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), jsonPath);
        }
        return hashCode;
    }
}
