package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpRequestValidator extends JsonSchemaValidator {

    private JsonSchemaHttpRequestValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            HttpRequest.class,
            "org/mockserver/model/schema/",
            "httpRequest",
            "stringOrJsonSchema",
            "body",
            "keyToMultiValue",
            "keyToValue",
            "socketAddress",
            "draft-07"
        );
    }

    private static JsonSchemaHttpRequestValidator jsonSchemaHttpRequestValidator;

    public static JsonSchemaHttpRequestValidator jsonSchemaHttpRequestValidator(MockServerLogger mockServerLogger) {
        if (jsonSchemaHttpRequestValidator == null) {
            jsonSchemaHttpRequestValidator = new JsonSchemaHttpRequestValidator(mockServerLogger);
        }
        return jsonSchemaHttpRequestValidator;
    }
}
