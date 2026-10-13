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
            "protocol",
            "draft-07"
        );
    }

    private JsonSchemaHttpRequestValidator(MockServerLogger mockServerLogger, JsonSchemaHttpRequestValidator compiled) {
        super(compiled, mockServerLogger);
    }

    static final CompiledOnce<JsonSchemaHttpRequestValidator> COMPILED = new CompiledOnce<>(() -> new JsonSchemaHttpRequestValidator(new MockServerLogger(JsonSchemaHttpRequestValidator.class)));

    /**
     * A validator that logs to {@code mockServerLogger}. The schema is compiled once per JVM, by a validator that
     * holds no server's logger, so a stopped server is not kept by it.
     */
    public static JsonSchemaHttpRequestValidator jsonSchemaHttpRequestValidator(MockServerLogger mockServerLogger) {
        return new JsonSchemaHttpRequestValidator(mockServerLogger, COMPILED.get());
    }
}
