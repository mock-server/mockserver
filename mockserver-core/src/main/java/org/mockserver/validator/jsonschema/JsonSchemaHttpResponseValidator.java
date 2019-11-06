package org.mockserver.validator.jsonschema;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpResponseValidator extends JsonSchemaValidator {

    private JsonSchemaHttpResponseValidator() {
        super(
            "org/mockserver/model/schema/",
            "httpResponse",
            "bodyWithContentType",
            "delay",
            "connectionOptions",
            "keyToMultiValue",
            "keyToValue"
        );
    }

    private static JsonSchemaHttpResponseValidator jsonSchemaHttpResponseValidator = new JsonSchemaHttpResponseValidator();

    public static JsonSchemaHttpResponseValidator jsonSchemaHttpResponseValidator() {
        if (jsonSchemaHttpResponseValidator == null) {
            jsonSchemaHttpResponseValidator = new JsonSchemaHttpResponseValidator();
        }
        return jsonSchemaHttpResponseValidator;
    }

}
