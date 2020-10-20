package org.mockserver.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.JsonSchemaBody;
import org.mockserver.model.ParameterStyle;

import java.util.Map;

/**
 * @author jamesdbloom
 */
public class JsonSchemaBodyDTO extends BodyDTO {

    private final String jsonSchema;
    private final Map<String, ParameterStyle> parameterStyles;

    public JsonSchemaBodyDTO(JsonSchemaBody jsonSchemaBody) {
        this(jsonSchemaBody, null);
    }

    public JsonSchemaBodyDTO(JsonSchemaBody jsonSchemaBody, Boolean not) {
        super(Body.Type.JSON_SCHEMA, not);
        this.jsonSchema = jsonSchemaBody.getValue();
        this.parameterStyles = jsonSchemaBody.getParameterStyles();
        withOptional(jsonSchemaBody.getOptional());
    }

    public String getJson() {
        return jsonSchema;
    }

    public Map<String, ParameterStyle> getParameterStyles() {
        return parameterStyles;
    }

    public JsonSchemaBody buildObject() {
        return (JsonSchemaBody) new JsonSchemaBody(getJson()).withParameterStyles(parameterStyles).withOptional(getOptional());
    }
}
