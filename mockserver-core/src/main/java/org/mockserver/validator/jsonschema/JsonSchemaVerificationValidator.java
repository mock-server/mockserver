package org.mockserver.validator.jsonschema;

/**
 * @author jamesdbloom
 */
public class JsonSchemaVerificationValidator extends JsonSchemaValidator {

    public JsonSchemaVerificationValidator() {
        super(
            "org/mockserver/model/schema/",
            "verification",
            "httpRequest",
            "body",
            "keyToMultiValue",
            "keyToValue",
            "verificationTimes"
        );
    }

    private static JsonSchemaVerificationValidator jsonSchemaVerificationValidator = new JsonSchemaVerificationValidator();

    public static JsonSchemaVerificationValidator jsonSchemaVerificationValidator() {
        if (jsonSchemaVerificationValidator == null) {
            jsonSchemaVerificationValidator = new JsonSchemaVerificationValidator();
        }
        return jsonSchemaVerificationValidator;
    }

}
