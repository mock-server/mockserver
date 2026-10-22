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
            "conditionalRequestDefinition",
            "anyOfRequestDefinition",
            "openAPIDefinition",
            "httpRequest",
            "stringOrJsonSchema",
            "body",
            "keyToMultiValue",
            "keyToValue",
            "socketAddress",
            "protocol",
            "httpResponse",
            "bodyWithContentType",
            "delay",
            "connectionOptions",
            "recoverAfter",
            "draft-07"
        );
    }

    private JsonSchemaHttpRequestAndHttpResponseValidator(MockServerLogger mockServerLogger, JsonSchemaHttpRequestAndHttpResponseValidator compiled) {
        super(compiled, mockServerLogger);
    }

    static final CompiledOnce<JsonSchemaHttpRequestAndHttpResponseValidator> COMPILED = new CompiledOnce<>(() -> new JsonSchemaHttpRequestAndHttpResponseValidator(new MockServerLogger(JsonSchemaHttpRequestAndHttpResponseValidator.class)));

    /**
     * A validator that logs to {@code mockServerLogger}. The schema is compiled once per JVM, by a validator that
     * holds no server's logger, so a stopped server is not kept by it.
     */
    public static JsonSchemaHttpRequestAndHttpResponseValidator jsonSchemaHttpRequestAndHttpResponseValidator(MockServerLogger mockServerLogger) {
        return new JsonSchemaHttpRequestAndHttpResponseValidator(mockServerLogger, COMPILED.get());
    }
}
