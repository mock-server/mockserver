package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpResponseValidator extends JsonSchemaValidator {

    private JsonSchemaHttpResponseValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            HttpResponse.class,
            "org/mockserver/model/schema/",
            "httpResponse",
            "stringOrJsonSchema",
            "bodyWithContentType",
            "delay",
            "connectionOptions",
            "keyToMultiValue",
            "keyToValue",
            "draft-07"
        );
    }

    private static JsonSchemaHttpResponseValidator jsonSchemaHttpResponseValidator;

    public static JsonSchemaHttpResponseValidator jsonSchemaHttpResponseValidator(MockServerLogger mockServerLogger) {
        if (jsonSchemaHttpResponseValidator == null) {
            jsonSchemaHttpResponseValidator = new JsonSchemaHttpResponseValidator(mockServerLogger);
        }
        return jsonSchemaHttpResponseValidator;
    }

}
