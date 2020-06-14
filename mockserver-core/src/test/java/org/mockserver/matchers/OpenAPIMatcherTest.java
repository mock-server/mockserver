package org.mockserver.matchers;

import org.junit.Ignore;
import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.OpenAPIDefinition;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.fail;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.MatchDifference.Field.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;

/**
 * @author jamesdbloom
 */
public class OpenAPIMatcherTest {

    private final MockServerLogger mockServerLogger = new MockServerLogger(OpenAPIMatcherTest.class);

    @Test
    public void shouldHandleBlankStrings() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("")
                .withOperationId("")
        );
        HttpRequest httpRequest = request();
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    @Test
    public void shouldHandleNulls() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload(null)
                .withOperationId(null)
        );
        HttpRequest httpRequest = request();
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    @Test
    public void shouldHandleInvalidOpenAPIJsonSpec() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);

        try {
            // when
            openAPIMatcher.update(
                new OpenAPIDefinition()
                    .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.json").substring(0, 100))
                    .withOperationId("listPets")
            );

            // then
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec from provided URL or payload because malformed or unreadable swagger supplied"));
        }
    }

    @Test
    public void shouldHandleInvalidOpenAPIYamlSpec() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);

        try {
            // when
            openAPIMatcher.update(
                new OpenAPIDefinition()
                    .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.yaml").substring(0, 100))
                    .withOperationId("listPets")
            );

            // then
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec from provided URL or payload because malformed or unreadable swagger supplied"));
        }
    }

    @Test
    public void shouldHandleInvalidOpenAPIUrl() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);

        try {
            // when
            openAPIMatcher.update(
                new OpenAPIDefinition()
                    .withSpecUrlOrPayload("org/mockserver/mock/does_not_exist.json")
                    .withOperationId("listPets")
            );

            // then
            fail("expected exception");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec from provided URL or payload because Unable to load API spec from provided URL or payload"));
        }
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIJsonUrl() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("listPets")
        );
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIJsonSpec() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.json"))
                .withOperationId("listPets")
        );
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIYamlUrl() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.yaml")
                .withOperationId("listPets")
        );
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    @Test
    public void shouldMatchSingleOperationInOpenAPIYamlSpec() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.yaml"))
                .withOperationId("listPets")
        );
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    @Test
    public void shouldMatchAnyOperationInOpenAPI() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("")
        );
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(matchDifference, httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, true);
    }

    @Test
    public void shouldNotMatchRequestInOpenAPIWithWrongOperationId() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("showPetById")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), containsInAnyOrder("  expected match against operation" + NEW_LINE +
            NEW_LINE +
            "    showPetById" + NEW_LINE +
            NEW_LINE +
            "   but request matched operation" + NEW_LINE +
            NEW_LINE +
            "    listPets" + NEW_LINE));
        assertThat(matchDifference.getDifferences(SPECIFICATION), nullValue());
    }

    @Test
    public void shouldNotMatchRequestInOpenAPIWithWrongOperationIdWithNullContext() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("showPetById")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(httpRequest);

        // then
        thenMatchesEmptyFieldDifferences(matchDifference, matches, false);
    }

    @Test
    public void shouldNotMatchRequestInOpenAPIWithMethodMismatch() {
        ConfigurationProperties.logLevel("DEBUG");

        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("showPetById")
        ));
        HttpRequest httpRequest = request()
            .withMethod("PUT")
            .withPath("/pets")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(SPECIFICATION), containsInAnyOrder("  PUT operation not allowed on path '/pets'"));
    }

    @Test
    public void shouldNotMatchRequestInOpenAPIWithPathMismatch() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("showPetById")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/wrong")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(SPECIFICATION), containsInAnyOrder("  No API path found that matches request '/wrong'"));
    }

    @Test
    public void shouldNotMatchRequestInOpenAPIWithHeaderMismatch() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("somePath")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/some/path")
            .withQueryStringParameter("limit", "10");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(SPECIFICATION), containsInAnyOrder("  Header parameter 'X-Request-ID' is required on path '/some/path' but not found in request"));
    }

    @Test
    public void shouldNotMatchRequestInOpenAPIWithHeaderAndQueryParameterMismatch() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("somePath")
        ));
        HttpRequest httpRequest = request()
            .withMethod("GET")
            .withPath("/some/path")
            .withQueryStringParameter("limit", "not_a_number");
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(SPECIFICATION), containsInAnyOrder(
            "  Header parameter 'X-Request-ID' is required on path '/some/path' but not found in request",
            "  Instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])"
        ));
    }

    @Test
    public void shouldNotMatchRequestInOpenAPIWithBodyMismatchWithContentType() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("createPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("POST")
            .withPath("/pets")
            .withHeader("content-type", "application/json")
            .withBody(json("{\n" +
                "    \"id\": \"invalid_id_format\", \n" +
                "    \"name\": \"scruffles\", \n" +
                "    \"tag\": \"dog\"\n" +
                "}"));
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(SPECIFICATION), containsInAnyOrder(
            "  [Path '/id'] Instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])"
        ));
    }

    @Test
    @Ignore("bug in validator library https://bitbucket.org/atlassian/swagger-request-validator/issues/238/request-with-no-content-type-header-are")
    public void shouldNotMatchRequestInOpenAPIWithBodyMismatch() {
        // given
        OpenAPIMatcher openAPIMatcher = new OpenAPIMatcher(mockServerLogger);
        openAPIMatcher.update(new Expectation(
            new OpenAPIDefinition()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_petstore_example.json")
                .withOperationId("createPets")
        ));
        HttpRequest httpRequest = request()
            .withMethod("POST")
            .withPath("/pets")
            .withBody(json("{\n" +
                "    \"id\": \"invalid_id_format\", \n" +
                "    \"name\": \"scruffles\", \n" +
                "    \"tag\": \"dog\"\n" +
                "}"));
        MatchDifference matchDifference = new MatchDifference(httpRequest);

        // when
        boolean matches = openAPIMatcher.matches(matchDifference, httpRequest);

        // then
        assertThat(matches, is(false));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(SPECIFICATION), containsInAnyOrder(
            "  [Path '/id'] Instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])"
        ));
    }

    private void thenMatchesEmptyFieldDifferences(MatchDifference matchDifference, boolean matches, boolean expected) {
        assertThat(matches, is(expected));
        assertThat(matchDifference.getDifferences(METHOD), nullValue());
        assertThat(matchDifference.getDifferences(PATH), nullValue());
        assertThat(matchDifference.getDifferences(QUERY), nullValue());
        assertThat(matchDifference.getDifferences(COOKIES), nullValue());
        assertThat(matchDifference.getDifferences(HEADERS), nullValue());
        assertThat(matchDifference.getDifferences(BODY), nullValue());
        assertThat(matchDifference.getDifferences(SSL_MATCHES), nullValue());
        assertThat(matchDifference.getDifferences(KEEP_ALIVE), nullValue());
        assertThat(matchDifference.getDifferences(OPERATION), nullValue());
        assertThat(matchDifference.getDifferences(SPECIFICATION), nullValue());
    }

}