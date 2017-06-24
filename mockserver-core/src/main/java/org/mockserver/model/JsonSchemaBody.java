package org.mockserver.model;

import com.google.common.net.MediaType;
import org.mockserver.file.FileReader;

/**
 * @author jamesdbloom
 */
public class JsonSchemaBody extends Body {

    public static final MediaType DEFAULT_CONTENT_TYPE = MediaType.create("application", "json");
    private final String jsonSchema;

    public JsonSchemaBody(String jsonSchema) {
        super(Type.JSON_SCHEMA, DEFAULT_CONTENT_TYPE);
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
}
