package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.RequestDefinition;

/**
 * @author jamesdbloom
 */
public class JsonSchemaRequestDefinitionValidator extends JsonSchemaValidator {

    private JsonSchemaRequestDefinitionValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            RequestDefinition.class,
            "org/mockserver/model/schema/",
            "requestDefinition",
            "httpRequest",
            "stringOrJsonSchema",
            "openAPIDefinition",
            "body",
            "keyToMultiValue",
            "keyToValue",
            "socketAddress",
            "openAPIDefinition",
            "draft-07"
        );
    }

    private static JsonSchemaRequestDefinitionValidator jsonSchemaHttpRequestValidator;

    public static JsonSchemaRequestDefinitionValidator jsonSchemaRequestDefinitionValidator(MockServerLogger mockServerLogger) {
        if (jsonSchemaHttpRequestValidator == null) {
            jsonSchemaHttpRequestValidator = new JsonSchemaRequestDefinitionValidator(mockServerLogger);
        }
        return jsonSchemaHttpRequestValidator;
    }
}
