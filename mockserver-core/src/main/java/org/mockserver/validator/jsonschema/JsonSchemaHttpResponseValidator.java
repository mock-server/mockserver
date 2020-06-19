package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpResponseValidator extends JsonSchemaValidator {

    private JsonSchemaHttpResponseValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            "org/mockserver/model/schema/",
            "httpResponse",
            "stringOrJsonSchema",
            "bodyWithContentType",
            "delay",
            "connectionOptions",
            "keyToMultiValue",
            "keyToValue"
        );
    }

    private static JsonSchemaHttpResponseValidator jsonSchemaHttpResponseValidator;

    public static JsonSchemaHttpResponseValidator jsonSchemaHttpResponseValidator(MockServerLogger mockServerLogger) {
        if (jsonSchemaHttpResponseValidator == null) {
            jsonSchemaHttpResponseValidator = new JsonSchemaHttpResponseValidator(mockServerLogger);
        }
        return jsonSchemaHttpResponseValidator;
    }

}
