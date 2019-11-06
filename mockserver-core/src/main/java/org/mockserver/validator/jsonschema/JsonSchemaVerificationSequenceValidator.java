package org.mockserver.validator.jsonschema;

/**
 * @author jamesdbloom
 */
public class JsonSchemaVerificationSequenceValidator extends JsonSchemaValidator {

    public JsonSchemaVerificationSequenceValidator() {
        super(
            "org/mockserver/model/schema/",
            "verificationSequence",
            "httpRequest",
            "body",
            "keyToMultiValue",
            "keyToValue"
        );
    }

    private static JsonSchemaVerificationSequenceValidator jsonSchemaVerificationSequenceValidator = new JsonSchemaVerificationSequenceValidator();

    public static JsonSchemaVerificationSequenceValidator jsonSchemaVerificationSequenceValidator() {
        if (jsonSchemaVerificationSequenceValidator == null) {
            jsonSchemaVerificationSequenceValidator = new JsonSchemaVerificationSequenceValidator();
        }
        return jsonSchemaVerificationSequenceValidator;
    }
}
