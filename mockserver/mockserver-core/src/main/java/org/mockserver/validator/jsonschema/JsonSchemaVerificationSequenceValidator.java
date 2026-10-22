package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.verify.VerificationSequence;

/**
 * @author jamesdbloom
 */
public class JsonSchemaVerificationSequenceValidator extends JsonSchemaValidator {

    private JsonSchemaVerificationSequenceValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            VerificationSequence.class,
            "org/mockserver/model/schema/",
            "verificationSequence",
            "expectationId",
            "requestDefinition",
            "conditionalRequestDefinition",
            "anyOfRequestDefinition",
            "openAPIDefinition",
            "binaryRequestDefinition",
            "dnsRequestDefinition",
            "httpRequest",
            "httpResponse",
            "stringOrJsonSchema",
            "body",
            "bodyWithContentType",
            "keyToMultiValue",
            "keyToValue",
            "connectionOptions",
            "delay",
            "socketAddress",
            "protocol",
            "draft-07"
        );
    }

    private JsonSchemaVerificationSequenceValidator(MockServerLogger mockServerLogger, JsonSchemaVerificationSequenceValidator compiled) {
        super(compiled, mockServerLogger);
    }

    static final CompiledOnce<JsonSchemaVerificationSequenceValidator> COMPILED = new CompiledOnce<>(() -> new JsonSchemaVerificationSequenceValidator(new MockServerLogger(JsonSchemaVerificationSequenceValidator.class)));

    /**
     * A validator that logs to {@code mockServerLogger}. The schema is compiled once per JVM, by a validator that
     * holds no server's logger, so a stopped server is not kept by it.
     */
    public static JsonSchemaVerificationSequenceValidator jsonSchemaVerificationSequenceValidator(MockServerLogger mockServerLogger) {
        return new JsonSchemaVerificationSequenceValidator(mockServerLogger, COMPILED.get());
    }
}
