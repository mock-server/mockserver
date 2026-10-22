package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.RequestDefinition;

/**
 * @author jamesdbloom
 */
public class JsonSchemaRequestDefinitionValidator extends JsonSchemaValidator {

    private JsonSchemaRequestDefinitionValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            RequestDefinition.class,
            "org/mockserver/model/schema/",
            "requestDefinition",
            "conditionalRequestDefinition",
            "anyOfRequestDefinition",
            "httpRequest",
            "stringOrJsonSchema",
            "openAPIDefinition",
            "binaryRequestDefinition",
            "dnsRequestDefinition",
            "body",
            "keyToMultiValue",
            "keyToValue",
            "socketAddress",
            "protocol",
            "draft-07"
        );
    }

    private JsonSchemaRequestDefinitionValidator(MockServerLogger mockServerLogger, JsonSchemaRequestDefinitionValidator compiled) {
        super(compiled, mockServerLogger);
    }

    static final CompiledOnce<JsonSchemaRequestDefinitionValidator> COMPILED = new CompiledOnce<>(() -> new JsonSchemaRequestDefinitionValidator(new MockServerLogger(JsonSchemaRequestDefinitionValidator.class)));

    /**
     * A validator that logs to {@code mockServerLogger}. The schema is compiled once per JVM, by a validator that
     * holds no server's logger, so a stopped server is not kept by it.
     */
    public static JsonSchemaRequestDefinitionValidator jsonSchemaRequestDefinitionValidator(MockServerLogger mockServerLogger) {
        return new JsonSchemaRequestDefinitionValidator(mockServerLogger, COMPILED.get());
    }
}
