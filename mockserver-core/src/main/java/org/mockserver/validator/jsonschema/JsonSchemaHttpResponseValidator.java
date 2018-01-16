package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpResponseValidator extends JsonSchemaValidator {

    public JsonSchemaHttpResponseValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            "org/mockserver/model/schema/",
            "httpResponse",
            "bodyWithContentType",
            "delay",
            "connectionOptions",
            "keyToMultiValue",
            "keyToValue"
        );
    }

}
