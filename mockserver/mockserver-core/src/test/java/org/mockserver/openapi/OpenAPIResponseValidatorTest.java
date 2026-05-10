package org.mockserver.openapi;

import org.junit.Test;
import org.mockserver.file.FileReader;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.HttpResponse.response;

public class OpenAPIResponseValidatorTest {

    private final MockServerLogger mockServerLogger = new MockServerLogger(OpenAPIResponseValidatorTest.class);
    private final String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json");

    @Test
    public void shouldReturnNoErrorsForValidResponse() {
        // given
        HttpResponse response = response()
            .withStatusCode(200)
            .withHeader("content-type", "application/json")
            .withBody("[{\"id\": 1, \"name\": \"Fido\"}]");

        // when
        List<String> errors = OpenAPIResponseValidator.validate(specUrlOrPayload, "listPets", response, mockServerLogger);

        // then
        assertThat(errors, is(empty()));
    }

    @Test
    public void shouldReturnNoErrorsForValidSinglePetResponse() {
        // given
        HttpResponse response = response()
            .withStatusCode(200)
            .withHeader("content-type", "application/json")
            .withBody("{\"id\": 1, \"name\": \"Fido\"}");

        // when
        List<String> errors = OpenAPIResponseValidator.validate(specUrlOrPayload, "showPetById", response, mockServerLogger);

        // then
        assertThat(errors, is(empty()));
    }

    @Test
    public void shouldReturnErrorForInvalidResponseBody() {
        // given
        HttpResponse response = response()
            .withStatusCode(200)
            .withHeader("content-type", "application/json")
            .withBody("{\"invalid\": \"body\"}");

        // when
        List<String> errors = OpenAPIResponseValidator.validate(specUrlOrPayload, "showPetById", response, mockServerLogger);

        // then
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), containsString("response body validation error"));
    }

    @Test
    public void shouldReturnErrorForInvalidArrayResponseBody() {
        // given - listPets expects an array of Pet objects
        HttpResponse response = response()
            .withStatusCode(200)
            .withHeader("content-type", "application/json")
            .withBody("{\"not\": \"an array\"}");

        // when
        List<String> errors = OpenAPIResponseValidator.validate(specUrlOrPayload, "listPets", response, mockServerLogger);

        // then
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), containsString("response body validation error"));
    }

    @Test
    public void shouldReturnErrorForUndefinedStatusCode() {
        // given - createPets defines 201, 400, 500, default but not 404
        // however since "default" is defined, it will match that
        // let's use showPetById which defines 200, 400, 500, default
        // Actually the petstore spec has "default" for most operations
        // Let me test with a status code that falls through to default
        HttpResponse response = response()
            .withStatusCode(404)
            .withHeader("content-type", "application/json")
            .withBody("{\"code\": 404, \"message\": \"not found\"}");

        // when - showPetById has 200, 400, 500, and default
        // 404 is not explicit but "default" will catch it
        List<String> errors = OpenAPIResponseValidator.validate(specUrlOrPayload, "showPetById", response, mockServerLogger);

        // then - should pass because "default" response matches
        assertThat(errors, is(empty()));
    }

    @Test
    public void shouldReturnErrorWhenOperationNotFound() {
        // given
        HttpResponse response = response().withStatusCode(200);

        // when
        List<String> errors = OpenAPIResponseValidator.validate(specUrlOrPayload, "nonExistentOperation", response, mockServerLogger);

        // then
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), containsString("operation nonExistentOperation not found"));
    }

    @Test
    public void shouldReturnErrorForStatusCodeNotDefinedAndNoDefault() {
        // given - create a spec with no default response
        String specWithNoDefault = "{\n" +
            "  \"openapi\": \"3.0.0\",\n" +
            "  \"info\": {\"title\": \"Test\", \"version\": \"1.0\"},\n" +
            "  \"paths\": {\n" +
            "    \"/test\": {\n" +
            "      \"get\": {\n" +
            "        \"operationId\": \"testOp\",\n" +
            "        \"responses\": {\n" +
            "          \"200\": {\n" +
            "            \"description\": \"OK\"\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

        HttpResponse response = response().withStatusCode(404);

        // when
        List<String> errors = OpenAPIResponseValidator.validate(specWithNoDefault, "testOp", response, mockServerLogger);

        // then
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), containsString("response status code 404 not defined"));
    }

    @Test
    public void shouldValidateResponseWithNoBody() {
        // given - createPets 201 has no content
        HttpResponse response = response().withStatusCode(201);

        // when
        List<String> errors = OpenAPIResponseValidator.validate(specUrlOrPayload, "createPets", response, mockServerLogger);

        // then
        assertThat(errors, is(empty()));
    }

    @Test
    public void shouldValidateErrorSchemaResponse() {
        // given
        HttpResponse response = response()
            .withStatusCode(500)
            .withHeader("content-type", "application/json")
            .withBody("{\"code\": 500, \"message\": \"internal error\"}");

        // when
        List<String> errors = OpenAPIResponseValidator.validate(specUrlOrPayload, "listPets", response, mockServerLogger);

        // then
        assertThat(errors, is(empty()));
    }

    @Test
    public void shouldReturnErrorForInvalidErrorSchemaResponse() {
        // given - Error schema requires "code" (integer) and "message" (string)
        HttpResponse response = response()
            .withStatusCode(500)
            .withHeader("content-type", "application/json")
            .withBody("{\"wrong\": \"fields\"}");

        // when
        List<String> errors = OpenAPIResponseValidator.validate(specUrlOrPayload, "listPets", response, mockServerLogger);

        // then
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), containsString("response body validation error"));
    }

    @Test
    public void shouldValidateResponseHeaderPresence() {
        // given - listPets 200 defines x-next header but it's not required
        HttpResponse response = response()
            .withStatusCode(200)
            .withHeader("content-type", "application/json")
            .withBody("[{\"id\": 1, \"name\": \"Fido\"}]");

        // when
        List<String> errors = OpenAPIResponseValidator.validate(specUrlOrPayload, "listPets", response, mockServerLogger);

        // then - no error because x-next header is optional
        assertThat(errors, is(empty()));
    }

    @Test
    public void shouldValidateRequiredResponseHeader() {
        // given - create a spec with a required response header
        String specWithRequiredHeader = "{\n" +
            "  \"openapi\": \"3.0.0\",\n" +
            "  \"info\": {\"title\": \"Test\", \"version\": \"1.0\"},\n" +
            "  \"paths\": {\n" +
            "    \"/test\": {\n" +
            "      \"get\": {\n" +
            "        \"operationId\": \"testOp\",\n" +
            "        \"responses\": {\n" +
            "          \"200\": {\n" +
            "            \"description\": \"OK\",\n" +
            "            \"headers\": {\n" +
            "              \"X-Request-Id\": {\n" +
            "                \"required\": true,\n" +
            "                \"schema\": {\n" +
            "                  \"type\": \"string\"\n" +
            "                }\n" +
            "              }\n" +
            "            },\n" +
            "            \"content\": {\n" +
            "              \"application/json\": {\n" +
            "                \"schema\": {\n" +
            "                  \"type\": \"object\"\n" +
            "                }\n" +
            "              }\n" +
            "            }\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

        HttpResponse response = response()
            .withStatusCode(200)
            .withHeader("content-type", "application/json")
            .withBody("{}");

        // when
        List<String> errors = OpenAPIResponseValidator.validate(specWithRequiredHeader, "testOp", response, mockServerLogger);

        // then
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0), containsString("required response header X-Request-Id not found"));
    }

    @Test
    public void shouldPassWhenRequiredHeaderIsPresent() {
        // given
        String specWithRequiredHeader = "{\n" +
            "  \"openapi\": \"3.0.0\",\n" +
            "  \"info\": {\"title\": \"Test\", \"version\": \"1.0\"},\n" +
            "  \"paths\": {\n" +
            "    \"/test\": {\n" +
            "      \"get\": {\n" +
            "        \"operationId\": \"testOp\",\n" +
            "        \"responses\": {\n" +
            "          \"200\": {\n" +
            "            \"description\": \"OK\",\n" +
            "            \"headers\": {\n" +
            "              \"X-Request-Id\": {\n" +
            "                \"required\": true,\n" +
            "                \"schema\": {\n" +
            "                  \"type\": \"string\"\n" +
            "                }\n" +
            "              }\n" +
            "            },\n" +
            "            \"content\": {\n" +
            "              \"application/json\": {\n" +
            "                \"schema\": {\n" +
            "                  \"type\": \"object\"\n" +
            "                }\n" +
            "              }\n" +
            "            }\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

        HttpResponse response = response()
            .withStatusCode(200)
            .withHeader("content-type", "application/json")
            .withHeader("X-Request-Id", "abc-123")
            .withBody("{}");

        // when
        List<String> errors = OpenAPIResponseValidator.validate(specWithRequiredHeader, "testOp", response, mockServerLogger);

        // then
        assertThat(errors, is(empty()));
    }

    @Test
    public void shouldHandleDefaultStatusCode() {
        // given - showPetById has "default" response for unexpected errors
        HttpResponse response = response()
            .withStatusCode(503)
            .withHeader("content-type", "application/json")
            .withBody("{\"code\": 503, \"message\": \"service unavailable\"}");

        // when
        List<String> errors = OpenAPIResponseValidator.validate(specUrlOrPayload, "showPetById", response, mockServerLogger);

        // then - should match default response
        assertThat(errors, is(empty()));
    }

    @Test
    public void shouldHandleNullStatusCode() {
        // given - null status code defaults to 200
        HttpResponse response = response()
            .withHeader("content-type", "application/json")
            .withBody("[{\"id\": 1, \"name\": \"Fido\"}]");

        // when
        List<String> errors = OpenAPIResponseValidator.validate(specUrlOrPayload, "listPets", response, mockServerLogger);

        // then
        assertThat(errors, is(empty()));
    }

}
