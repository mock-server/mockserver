package org.mockserver.validator.jsonschema;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpRequestValidator extends JsonSchemaValidator {

    private JsonSchemaHttpRequestValidator() {
        super(
            "org/mockserver/model/schema/",
            "httpRequest",
            "body",
            "keyToMultiValue",
            "keyToValue");
    }

    private static JsonSchemaHttpRequestValidator jsonSchemaHttpRequestValidator = new JsonSchemaHttpRequestValidator();

    public static JsonSchemaHttpRequestValidator jsonSchemaHttpRequestValidator() {
        if (jsonSchemaHttpRequestValidator == null) {
            jsonSchemaHttpRequestValidator = new JsonSchemaHttpRequestValidator();
        }
        return jsonSchemaHttpRequestValidator;
    }
}
