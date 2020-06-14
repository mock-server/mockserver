package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class JsonSchemaOpenAPIExpectationValidator extends JsonSchemaValidator {

    private JsonSchemaOpenAPIExpectationValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            "org/mockserver/model/schema/",
            "openAPIExpectation"
        );
    }

    private static JsonSchemaOpenAPIExpectationValidator jsonSchemaExpectationValidator;

    public static JsonSchemaOpenAPIExpectationValidator jsonSchemaOpenAPIExpectationValidator(MockServerLogger mockServerLogger) {
        if (jsonSchemaExpectationValidator == null) {
            jsonSchemaExpectationValidator = new JsonSchemaOpenAPIExpectationValidator(mockServerLogger);
        }
        return jsonSchemaExpectationValidator;
    }
}
