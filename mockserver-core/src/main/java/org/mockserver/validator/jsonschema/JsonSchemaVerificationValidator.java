package org.mockserver.validator.jsonschema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class JsonSchemaVerificationValidator extends JsonSchemaValidator {

    public static Logger logger = LoggerFactory.getLogger(JsonSchemaVerificationValidator.class);
    private static String jsonSchema = addReferencesIntoSchema(
            "org/mockserver/model/schema/",
            "verification",
            "httpRequest",
            "body",
            "keyToMultiValue",
            "keyToValue",
            "verificationTimes"
    );

    public JsonSchemaVerificationValidator() {
        super(jsonSchema);
    }

    public String getSchema() {
        return jsonSchema;
    }
}
