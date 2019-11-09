package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpRequestValidator extends JsonSchemaValidator {

    private JsonSchemaHttpRequestValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            "org/mockserver/model/schema/",
            "httpRequest",
            "body",
            "keyToMultiValue",
            "keyToValue");
    }

    private static JsonSchemaHttpRequestValidator jsonSchemaHttpRequestValidator;

    public static JsonSchemaHttpRequestValidator jsonSchemaHttpRequestValidator(MockServerLogger mockServerLogger) {
        if (jsonSchemaHttpRequestValidator == null) {
            jsonSchemaHttpRequestValidator = new JsonSchemaHttpRequestValidator(mockServerLogger);
        }
        return jsonSchemaHttpRequestValidator;
    }
}
