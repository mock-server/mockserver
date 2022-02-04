package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequestAndHttpResponse;

/**
 * @author jamesdbloom
 */
public class JsonSchemaHttpRequestAndHttpResponseValidator extends JsonSchemaValidator {

    private JsonSchemaHttpRequestAndHttpResponseValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            HttpRequestAndHttpResponse.class,
            "org/mockserver/model/schema/",
            "httpRequestAndHttpResponse",
            "requestDefinition",
            "openAPIDefinition",
            "httpRequest",
            "stringOrJsonSchema",
            "body",
            "keyToMultiValue",
            "keyToValue",
            "socketAddress",
            "httpResponse",
            "bodyWithContentType",
            "delay",
            "connectionOptions"
        );
    }

    private static JsonSchemaHttpRequestAndHttpResponseValidator jsonSchemaHttpRequestValidator;

    public static JsonSchemaHttpRequestAndHttpResponseValidator jsonSchemaHttpRequestAndHttpResponseValidator(MockServerLogger mockServerLogger) {
        if (jsonSchemaHttpRequestValidator == null) {
            jsonSchemaHttpRequestValidator = new JsonSchemaHttpRequestAndHttpResponseValidator(mockServerLogger);
        }
        return jsonSchemaHttpRequestValidator;
    }
}
