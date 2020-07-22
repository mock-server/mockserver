package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class JsonSchemaExpectationIdValidator extends JsonSchemaValidator {

    private JsonSchemaExpectationIdValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            "org/mockserver/model/schema/",
            "expectationId"
        );
    }

    private static JsonSchemaExpectationIdValidator jsonSchemaExpectationIdValidator;

    public static JsonSchemaExpectationIdValidator jsonSchemaExpectationIdValidator(MockServerLogger mockServerLogger) {
        if (jsonSchemaExpectationIdValidator == null) {
            jsonSchemaExpectationIdValidator = new JsonSchemaExpectationIdValidator(mockServerLogger);
        }
        return jsonSchemaExpectationIdValidator;
    }
}
