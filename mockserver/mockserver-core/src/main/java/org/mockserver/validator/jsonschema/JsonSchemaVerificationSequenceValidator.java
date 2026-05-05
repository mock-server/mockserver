package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.verify.VerificationSequence;

/**
 * @author jamesdbloom
 */
public class JsonSchemaVerificationSequenceValidator extends JsonSchemaValidator {

    private JsonSchemaVerificationSequenceValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            VerificationSequence.class,
            "org/mockserver/model/schema/",
            "verificationSequence",
            "expectationId",
            "requestDefinition",
            "openAPIDefinition",
            "httpRequest",
            "stringOrJsonSchema",
            "body",
            "keyToMultiValue",
            "keyToValue",
            "socketAddress",
            "protocol",
            "draft-07"
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
