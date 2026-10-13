package org.mockserver.validator.jsonschema;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.OpenAPIExpectation;

/**
 * @author jamesdbloom
 */
public class JsonSchemaOpenAPIExpectationValidator extends JsonSchemaValidator {

    private JsonSchemaOpenAPIExpectationValidator(MockServerLogger mockServerLogger) {
        super(
            mockServerLogger,
            OpenAPIExpectation.class,
            "org/mockserver/model/schema/",
            "openAPIExpectation"
        );
    }

    private JsonSchemaOpenAPIExpectationValidator(MockServerLogger mockServerLogger, JsonSchemaOpenAPIExpectationValidator compiled) {
        super(compiled, mockServerLogger);
    }

    static final CompiledOnce<JsonSchemaOpenAPIExpectationValidator> COMPILED = new CompiledOnce<>(() -> new JsonSchemaOpenAPIExpectationValidator(new MockServerLogger(JsonSchemaOpenAPIExpectationValidator.class)));

    /**
     * A validator that logs to {@code mockServerLogger}. The schema is compiled once per JVM, by a validator that
     * holds no server's logger, so a stopped server is not kept by it.
     */
    public static JsonSchemaOpenAPIExpectationValidator jsonSchemaOpenAPIExpectationValidator(MockServerLogger mockServerLogger) {
        return new JsonSchemaOpenAPIExpectationValidator(mockServerLogger, COMPILED.get());
    }
}
