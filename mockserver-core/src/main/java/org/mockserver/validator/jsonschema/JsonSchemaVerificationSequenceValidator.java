package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class JsonSchemaVerificationSequenceValidator extends JsonSchemaValidator {

    private JsonSchemaVerificationSequenceValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            "org/mockserver/model/schema/",
            "verificationSequence",
            "requestDefinition",
            "httpRequest",
            "openAPIDefinition",
            "body",
            "keyToMultiValue",
            "keyToValue"
        );
    }

    private static JsonSchemaVerificationSequenceValidator jsonSchemaVerificationSequenceValidator;

    public static JsonSchemaVerificationSequenceValidator jsonSchemaVerificationSequenceValidator(MockServerLogger mockServerLogger) {
        if (jsonSchemaVerificationSequenceValidator == null) {
            jsonSchemaVerificationSequenceValidator = new JsonSchemaVerificationSequenceValidator(mockServerLogger);
        }
        return jsonSchemaVerificationSequenceValidator;
    }
}
