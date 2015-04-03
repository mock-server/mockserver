package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class JsonSchemaBody extends Body {

    private final String jsonSchema;

    public JsonSchemaBody(String jsonSchema) {
        super(Type.JSON_SCHEMA);
        this.jsonSchema = jsonSchema;
    }

    public static JsonSchemaBody jsonSchema(String jsonSchema) {
        return new JsonSchemaBody(jsonSchema);
    }

    public String getValue() {
        return jsonSchema;
    }

}
