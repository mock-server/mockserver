package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class JsonSchemaVerificationValidator extends JsonSchemaValidator {

    public JsonSchemaVerificationValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            "org/mockserver/model/schema/",
            "verification",
            "httpRequest",
            "body",
            "keyToMultiValue",
            "keyToValue",
            "verificationTimes"
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
