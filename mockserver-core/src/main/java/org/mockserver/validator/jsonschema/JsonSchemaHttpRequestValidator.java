package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpRequestValidator extends JsonSchemaValidator {

    public JsonSchemaHttpRequestValidator(MockServerLogger mockServerLogger) {
        super(mockServerLogger,
            "org/mockserver/model/schema/",
            "httpRequest",
            "body",
            "keyToMultiValue",
            "keyToValue");
    }
}
