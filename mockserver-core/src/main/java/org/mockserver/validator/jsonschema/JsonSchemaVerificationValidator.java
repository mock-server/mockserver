package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.verify.Verification;

/**
 * @author jamesdbloom
 */
public class JsonSchemaVerificationValidator extends JsonSchemaValidator {

    private JsonSchemaVerificationValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            Verification.class,
            "org/mockserver/model/schema/",
            "verification",
            "expectationId",
            "requestDefinition",
            "openAPIDefinition",
            "httpRequest",
            "stringOrJsonSchema",
            "body",
            "keyToMultiValue",
            "keyToValue",
            "verificationTimes",
            "socketAddress",
            "draft-07"
        );
    }

    private static JsonSchemaVerificationValidator jsonSchemaVerificationValidator;

    public static JsonSchemaVerificationValidator jsonSchemaVerificationValidator(MockServerLogger mockServerLogger) {
        if (jsonSchemaVerificationValidator == null) {
            jsonSchemaVerificationValidator = new JsonSchemaVerificationValidator(mockServerLogger);
        }
        return jsonSchemaVerificationValidator;
    }

}
