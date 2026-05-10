package org.mockserver.openapi;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.HttpRequest.request;

public class OpenAPIRequestValidatorTest {

    private static final String SPEC = "org/mockserver/openapi/openapi_petstore_example.json";
    private final MockServerLogger logger = new MockServerLogger();

    @Test
    public void shouldPassForValidGetRequest() {
        List<String> errors = OpenAPIRequestValidator.validate(SPEC,
            request("/pets").withMethod("GET"),
            logger
        );
        assertThat(errors, is(empty()));
    }

    @Test
    public void shouldFailForUnknownPathAndMethod() {
        List<String> errors = OpenAPIRequestValidator.validate(SPEC,
            request("/unknown/path").withMethod("DELETE"),
            logger
        );
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), containsString("no operation found"));
    }

    @Test
    public void shouldFailForMissingRequiredBody() {
        List<String> errors = OpenAPIRequestValidator.validate(SPEC,
            request("/pets").withMethod("POST"),
            logger
        );
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), containsString("request body is required but was empty"));
    }

    @Test
    public void shouldPassForValidPostBody() {
        List<String> errors = OpenAPIRequestValidator.validate(SPEC,
            request("/pets")
                .withMethod("POST")
                .withHeader("content-type", "application/json")
                .withBody("{\"id\": 1, \"name\": \"Fido\"}"),
            logger
        );
        assertThat(errors, is(empty()));
    }

    @Test
    public void shouldFailForInvalidPostBody() {
        List<String> errors = OpenAPIRequestValidator.validate(SPEC,
            request("/pets")
                .withMethod("POST")
                .withHeader("content-type", "application/json")
                .withBody("{\"id\": \"not_a_number\", \"name\": \"Fido\"}"),
            logger
        );
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), containsString("request body validation error"));
    }

    @Test
    public void shouldMatchPathWithTemplateParameters() {
        List<String> errors = OpenAPIRequestValidator.validate(SPEC,
            request("/pets/123").withMethod("GET"),
            logger
        );
        assertThat(errors, is(empty()));
    }

    @Test
    public void shouldFallbackToApplicationJsonContentType() {
        List<String> errors = OpenAPIRequestValidator.validate(SPEC,
            request("/pets")
                .withMethod("POST")
                .withBody("{\"id\": 1, \"name\": \"Fido\"}"),
            logger
        );
        assertThat(errors, is(empty()));
    }

    @Test
    public void shouldHandleContentTypeWithCharset() {
        List<String> errors = OpenAPIRequestValidator.validate(SPEC,
            request("/pets")
                .withMethod("POST")
                .withHeader("content-type", "application/json; charset=utf-8")
                .withBody("{\"id\": 1, \"name\": \"Fido\"}"),
            logger
        );
        assertThat(errors, is(empty()));
    }

    @Test
    public void shouldHandleInvalidSpec() {
        List<String> errors = OpenAPIRequestValidator.validate("not_a_valid_spec",
            request("/pets").withMethod("GET"),
            logger
        );
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), containsString("OpenAPI request validation error"));
    }
}
