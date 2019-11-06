package org.mockserver.validator.jsonschema;

/**
 * @author jamesdbloom
 */
public class JsonSchemaExpectationValidator extends JsonSchemaValidator {

    private JsonSchemaExpectationValidator() {
        super("org/mockserver/model/schema/",
            "expectation",
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
            "body",
            "bodyWithContentType",
            "delay",
            "connectionOptions",
            "keyToMultiValue",
            "keyToValue");
    }

    private static JsonSchemaExpectationValidator jsonSchemaExpectationValidator = new JsonSchemaExpectationValidator();

    public static JsonSchemaExpectationValidator jsonSchemaExpectationValidator() {
        if (jsonSchemaExpectationValidator == null) {
            jsonSchemaExpectationValidator = new JsonSchemaExpectationValidator();
        }
        return jsonSchemaExpectationValidator;
    }
}
