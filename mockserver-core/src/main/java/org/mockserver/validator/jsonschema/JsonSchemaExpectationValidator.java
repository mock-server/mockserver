package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;

/**
 * @author jamesdbloom
 */
public class JsonSchemaExpectationValidator extends JsonSchemaValidator {

    private JsonSchemaExpectationValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            "org/mockserver/model/schema/",
            "expectation",
            "requestDefinition",
            "openAPIDefinition",
            "httpRequest",
            "httpResponse",
            "httpTemplate",
            "httpForward",
            "httpClassCallback",
            "httpObjectCallback",
            "httpOverrideForwardedRequest",
            "httpError",
            "times",
            "timeToLive",
            "stringOrJsonSchema",
            "body",
            "bodyWithContentType",
            "delay",
            "connectionOptions",
            "keyToMultiValue",
            "keyToValue",
            "socketAddress"
        );
    }

    private static JsonSchemaExpectationValidator jsonSchemaExpectationValidator;

    public static JsonSchemaExpectationValidator jsonSchemaExpectationValidator(MockServerLogger mockServerLogger) {
        if (jsonSchemaExpectationValidator == null) {
            jsonSchemaExpectationValidator = new JsonSchemaExpectationValidator(mockServerLogger);
        }
        return jsonSchemaExpectationValidator;
    }
}
