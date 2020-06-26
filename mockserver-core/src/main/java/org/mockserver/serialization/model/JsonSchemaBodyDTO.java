package org.mockserver.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.JsonSchemaBody;

/**
 * @author jamesdbloom
 */
public class JsonSchemaBodyDTO extends BodyDTO {

    private final String jsonSchema;

    public JsonSchemaBodyDTO(JsonSchemaBody jsonSchemaBody) {
        this(jsonSchemaBody, null);
    }

    public JsonSchemaBodyDTO(JsonSchemaBody jsonSchemaBody, Boolean not) {
        super(Body.Type.JSON_SCHEMA, not);
        this.jsonSchema = jsonSchemaBody.getValue();
        withOptional(jsonSchemaBody.getOptional());
    }

    public String getJson() {
        return jsonSchema;
    }

    public JsonSchemaBody buildObject() {
        return (JsonSchemaBody) new JsonSchemaBody(getJson()).withOptional(getOptional());
    }
}
