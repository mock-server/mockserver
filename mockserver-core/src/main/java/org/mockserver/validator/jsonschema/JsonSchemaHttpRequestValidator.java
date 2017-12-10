package org.mockserver.validator.jsonschema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpRequestValidator extends JsonSchemaValidator {

    public static Logger logger = LoggerFactory.getLogger(JsonSchemaHttpRequestValidator.class);
    private static String jsonSchema = addReferencesIntoSchema(
        "org/mockserver/model/schema/",
        "httpRequest",
        "body",
        "keyToMultiValue",
        "keyToValue"
    );

    public JsonSchemaHttpRequestValidator() {
        super(jsonSchema);
    }

    public String getSchema() {
        return jsonSchema;
    }
}
