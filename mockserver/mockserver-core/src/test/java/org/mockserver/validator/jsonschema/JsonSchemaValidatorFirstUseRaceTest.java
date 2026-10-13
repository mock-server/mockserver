package org.mockserver.validator.jsonschema;

import com.networknt.schema.Schema;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Servers started at the same time ask for the schema validators at the same time. The schema must still be
 * compiled once, so every validator shares one compiled schema.
 * <p>
 * Runs in the sequential phase: it forgets each JVM-wide compiled schema, which would break a test running alongside.
 */
public class JsonSchemaValidatorFirstUseRaceTest {

    private static final int THREADS = 8;
    private static final long DEADLINE_SECONDS = 120;

    @Test
    public void anExpectationValidatorCompilesItsSchemaOnceWhenFirstUsedConcurrently() throws Exception {
        assertCompiledOnceWhenRaced(JsonSchemaExpectationValidator.COMPILED, JsonSchemaExpectationValidator::jsonSchemaExpectationValidator);
    }

    @Test
    public void aRequestDefinitionValidatorCompilesItsSchemaOnceWhenFirstUsedConcurrently() throws Exception {
        assertCompiledOnceWhenRaced(JsonSchemaRequestDefinitionValidator.COMPILED, JsonSchemaRequestDefinitionValidator::jsonSchemaRequestDefinitionValidator);
    }

    @Test
    public void anExpectationIdValidatorCompilesItsSchemaOnceWhenFirstUsedConcurrently() throws Exception {
        assertCompiledOnceWhenRaced(JsonSchemaExpectationIdValidator.COMPILED, JsonSchemaExpectationIdValidator::jsonSchemaExpectationIdValidator);
    }

    @Test
    public void anHttpRequestValidatorCompilesItsSchemaOnceWhenFirstUsedConcurrently() throws Exception {
        assertCompiledOnceWhenRaced(JsonSchemaHttpRequestValidator.COMPILED, JsonSchemaHttpRequestValidator::jsonSchemaHttpRequestValidator);
    }

    @Test
    public void anHttpResponseValidatorCompilesItsSchemaOnceWhenFirstUsedConcurrently() throws Exception {
        assertCompiledOnceWhenRaced(JsonSchemaHttpResponseValidator.COMPILED, JsonSchemaHttpResponseValidator::jsonSchemaHttpResponseValidator);
    }

    @Test
    public void anHttpRequestAndHttpResponseValidatorCompilesItsSchemaOnceWhenFirstUsedConcurrently() throws Exception {
        assertCompiledOnceWhenRaced(JsonSchemaHttpRequestAndHttpResponseValidator.COMPILED, JsonSchemaHttpRequestAndHttpResponseValidator::jsonSchemaHttpRequestAndHttpResponseValidator);
    }

    @Test
    public void anOpenAPIExpectationValidatorCompilesItsSchemaOnceWhenFirstUsedConcurrently() throws Exception {
        assertCompiledOnceWhenRaced(JsonSchemaOpenAPIExpectationValidator.COMPILED, JsonSchemaOpenAPIExpectationValidator::jsonSchemaOpenAPIExpectationValidator);
    }

    @Test
    public void aVerificationValidatorCompilesItsSchemaOnceWhenFirstUsedConcurrently() throws Exception {
        assertCompiledOnceWhenRaced(JsonSchemaVerificationValidator.COMPILED, JsonSchemaVerificationValidator::jsonSchemaVerificationValidator);
    }

    @Test
    public void aVerificationSequenceValidatorCompilesItsSchemaOnceWhenFirstUsedConcurrently() throws Exception {
        assertCompiledOnceWhenRaced(JsonSchemaVerificationSequenceValidator.COMPILED, JsonSchemaVerificationSequenceValidator::jsonSchemaVerificationSequenceValidator);
    }

    private static void assertCompiledOnceWhenRaced(CompiledOnce<?> compiled, Function<MockServerLogger, ? extends JsonSchemaValidator> validatorFor) throws Exception {
        compiled.reset();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<JsonSchemaValidator>> validators = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                validators.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return validatorFor.apply(new MockServerLogger());
                }));
            }
            assertThat("every thread reached the start line", ready.await(DEADLINE_SECONDS, TimeUnit.SECONDS), is(true));
            start.countDown();

            Set<Schema> compiledSchemas = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Future<JsonSchemaValidator> validator : validators) {
                compiledSchemas.add(compiledSchemaOf(validator.get(DEADLINE_SECONDS, TimeUnit.SECONDS)));
            }
            assertThat("distinct compiled schemas", compiledSchemas.size(), is(1));
        } finally {
            executor.shutdownNow();
        }
    }

    private static Schema compiledSchemaOf(JsonSchemaValidator validator) throws Exception {
        Field field = JsonSchemaValidator.class.getDeclaredField("validator");
        field.setAccessible(true);
        return (Schema) field.get(validator);
    }
}
