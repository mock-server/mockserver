package org.mockserver.validator.jsonschema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class JsonSchemaVerificationSequenceValidator extends JsonSchemaValidator {

    public static Logger logger = LoggerFactory.getLogger(JsonSchemaVerificationSequenceValidator.class);
    private static String jsonSchema = addReferencesIntoSchema(
        "org/mockserver/model/schema/",
        "verificationSequence",
        "httpRequest",
        "body",
        "keyToMultiValue",
        "keyToValue"
    );

    public JsonSchemaVerificationSequenceValidator() {
        super(jsonSchema);
    }

    public String getSchema() {
        return jsonSchema;
    }
}
