package org.mockserver.validator.jsonschema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpResponseValidator extends JsonSchemaValidator {

    public static Logger logger = LoggerFactory.getLogger(JsonSchemaHttpResponseValidator.class);
    private static String jsonSchema = addReferencesIntoSchema(
        "org/mockserver/model/schema/",
        "httpResponse",
        "bodyWithContentType",
        "delay",
        "connectionOptions",
        "keyToMultiValue",
        "keyToValue"
    );

    public JsonSchemaHttpResponseValidator() {
        super(jsonSchema);
    }

    public String getSchema() {
        return jsonSchema;
    }

}
