package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Charsets;
import com.google.common.net.MediaType;
import org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

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
        return new JsonSchemaBody(readFileFromClassPathOrPath(jsonSchemaPath));
    }

    private static String readFileFromClassPathOrPath(String filePath) {
        try {
            InputStream inputStream = JsonSchemaBody.class.getClassLoader().getResourceAsStream(filePath);
            if (inputStream == null) {
                inputStream = new FileInputStream(filePath);
            }
            return IOUtils.toString(inputStream, Charsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("Exception while loading \"" + filePath + "\"");
        }
    }

    public String getValue() {
        return jsonSchema;
    }
}
