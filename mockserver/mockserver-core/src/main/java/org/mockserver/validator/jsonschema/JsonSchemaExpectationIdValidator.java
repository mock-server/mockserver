package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ExpectationId;

/**
 * @author jamesdbloom
 */
public class JsonSchemaExpectationIdValidator extends JsonSchemaValidator {

    private JsonSchemaExpectationIdValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            ExpectationId.class,
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
