package org.mockserver.validator.jsonschema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class JsonSchemaExpectationValidator extends JsonSchemaValidator {

    public static Logger logger = LoggerFactory.getLogger(JsonSchemaExpectationValidator.class);
    private static String jsonSchema = addReferencesIntoSchema(
                "org/mockserver/model/schema/",
                "expectation",
                "httpRequest",
                "httpResponse",
                "httpForward",
                "httpClassCallback",
                "httpObjectCallback",
                "httpError",
                "times",
                "timeToLive",
                "body",
                "bodyWithContentType",
                "delay",
                "connectionOptions",
                "keyToMultiValue",
                "keyToValue"
    );

    public JsonSchemaExpectationValidator() {
        super(jsonSchema);
    }

    public String getSchema() {
        return jsonSchema;
    }
}
