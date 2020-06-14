package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class JsonSchemaRequestDefinitionValidator extends JsonSchemaValidator {

    private JsonSchemaRequestDefinitionValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            "org/mockserver/model/schema/",
            "requestDefinition",
            "httpRequest",
            "openAPIDefinition",
            "body",
            "keyToMultiValue",
            "keyToValue",
            "socketAddress",
            "openAPIDefinition"
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
