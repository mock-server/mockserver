package org.mockserver.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.JsonPathBody;

/**
 * @author jamesdbloom
 */
public class JsonPathBodyDTO extends BodyDTO {

    private final String jsonPath;

    public JsonPathBodyDTO(JsonPathBody jsonPathBody) {
        this(jsonPathBody, null);
    }

    public JsonPathBodyDTO(JsonPathBody jsonPathBody, Boolean not) {
        super(Body.Type.JSON_PATH, not);
        this.jsonPath = jsonPathBody.getValue();
        withOptional(jsonPathBody.getOptional());
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public JsonPathBody buildObject() {
        return new JsonPathBody(getJsonPath());
    }
}
