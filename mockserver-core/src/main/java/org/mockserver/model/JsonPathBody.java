package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class JsonPathBody extends Body {

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
}
