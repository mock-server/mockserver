package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ExpectationId;

/**
 * @author jamesdbloom
 */
public class JsonSchemaExpectationIdValidator extends JsonSchemaValidator {

    private JsonSchemaExpectationIdValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            ExpectationId.class,
            "org/mockserver/model/schema/",
            "expectationId"
        );
    }

    private JsonSchemaExpectationIdValidator(MockServerLogger mockServerLogger, JsonSchemaExpectationIdValidator compiled) {
        super(compiled, mockServerLogger);
    }

    static final CompiledOnce<JsonSchemaExpectationIdValidator> COMPILED = new CompiledOnce<>(() -> new JsonSchemaExpectationIdValidator(new MockServerLogger(JsonSchemaExpectationIdValidator.class)));

    /**
     * A validator that logs to {@code mockServerLogger}. The schema is compiled once per JVM, by a validator that
     * holds no server's logger, so a stopped server is not kept by it.
     */
    public static JsonSchemaExpectationIdValidator jsonSchemaExpectationIdValidator(MockServerLogger mockServerLogger) {
        return new JsonSchemaExpectationIdValidator(mockServerLogger, COMPILED.get());
    }
}
