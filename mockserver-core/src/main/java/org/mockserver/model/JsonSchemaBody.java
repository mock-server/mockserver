package org.mockserver.model;

import org.mockserver.file.FileReader;

import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class JsonSchemaBody extends Body<String> {
    private int hashCode;
    private final String jsonSchema;

    public JsonSchemaBody(String jsonSchema) {
        super(Type.JSON_SCHEMA);
        this.jsonSchema = jsonSchema;
    }

    public static JsonSchemaBody jsonSchema(String jsonSchema) {
        return new JsonSchemaBody(jsonSchema);
    }

    public static JsonSchemaBody jsonSchemaFromResource(String jsonSchemaPath) {
        return new JsonSchemaBody(FileReader.readFileFromClassPathOrPath(jsonSchemaPath));
    }

    public String getValue() {
        return jsonSchema;
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
        JsonSchemaBody that = (JsonSchemaBody) o;
        return Objects.equals(jsonSchema, that.jsonSchema);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), jsonSchema);
        }
        return hashCode;
    }
}
