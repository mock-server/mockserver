package org.mockserver.openapi;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.mockserver.file.FileReader;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.mock.Expectation.when;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

public class OpenAPIConverterTest {

    MockServerLogger mockServerLogger = new MockServerLogger(OpenAPIConverterTest.class);

    @Test
    public void shouldHandleAddOpenAPIJson() {
        // given
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.json");

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            null
        );

        // then
        shouldBuildPetStoreExpectations(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIJsonWithSpecificResponses() {
        // given
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.json");

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of(
                "listPets", "500",
                "createPets", "default",
                "showPetById", "200"
            )
        );

        // then
        shouldBuildPetStoreExpectationsWithSpecificResponses(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIYaml() {
        // given
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.yaml");

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            null
        );

        // then
        shouldBuildPetStoreExpectations(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIYamlWithSpecificResponses() {
        // given
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.yaml");

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of(
                "listPets", "500",
                "createPets", "default",
                "showPetById", "200"
            )
        );

        // then
        shouldBuildPetStoreExpectationsWithSpecificResponses(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIJsonClasspath() {
        // given
        String specUrlOrPayload = "org/mockserver/mock/openapi_petstore_example.json";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            null
        );

        // then
        shouldBuildPetStoreExpectations(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIJsonClasspathWithSpecificResponses() {
        // given
        String specUrlOrPayload = "org/mockserver/mock/openapi_petstore_example.json";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of(
                "listPets", "500",
                "createPets", "default",
                "showPetById", "200"
            )
        );

        // then
        shouldBuildPetStoreExpectationsWithSpecificResponses(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIYamlClasspath() {
        // given
        String specUrlOrPayload = "org/mockserver/mock/openapi_petstore_example.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            null
        );

        // then
        shouldBuildPetStoreExpectations(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIYamlClasspathWithSpecificResponses() {
        // given
        String specUrlOrPayload = "org/mockserver/mock/openapi_petstore_example.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of(
                "listPets", "500",
                "createPets", "default",
                "showPetById", "200"
            )
        );

        // then
        shouldBuildPetStoreExpectationsWithSpecificResponses(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIJsonUrl() {
        // given
        String specUrlOrPayload = FileReader.getURL("org/mockserver/mock/openapi_petstore_example.json").toString();

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            null
        );

        // then
        shouldBuildPetStoreExpectations(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIJsonUrlWithSpecificResponses() {
        // given
        String specUrlOrPayload = FileReader.getURL("org/mockserver/mock/openapi_petstore_example.json").toString();

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of(
                "listPets", "500",
                "createPets", "default",
                "showPetById", "200"
            )
        );

        // then
        shouldBuildPetStoreExpectationsWithSpecificResponses(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIYamlUrl() {
        // given
        String specUrlOrPayload = FileReader.getURL("org/mockserver/mock/openapi_petstore_example.yaml").toString();

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            null
        );

        // then
        shouldBuildPetStoreExpectations(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIYamlUrlWithSpecificResponses() {
        // given
        String specUrlOrPayload = FileReader.getURL("org/mockserver/mock/openapi_petstore_example.yaml").toString();

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of(
                "listPets", "500",
                "createPets", "default",
                "showPetById", "200"
            )
        );

        // then
        shouldBuildPetStoreExpectationsWithSpecificResponses(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIJsonWithSpecificationExamples() {
        // given
        String specUrlOrPayload = "org/mockserver/mock/openapi_petstore_example_with_examples.json";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            null
        );

        // then
        shouldBuildPetStoreExpectationsWithExamples(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIJsonWithSpecificResponsesWithSpecificationExamples() {
        // given
        String specUrlOrPayload = "org/mockserver/mock/openapi_petstore_example_with_examples.json";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of(
                "listPets", "500",
                "createPets", "default",
                "showPetById", "200"
            )
        );

        // then
        shouldBuildPetStoreExpectationsWithExamplesAndSpecificResponses(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIYamlWithSpecificationExamples() {
        // given
        String specUrlOrPayload = "org/mockserver/mock/openapi_petstore_example_with_examples.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            null
        );

        // then
        shouldBuildPetStoreExpectationsWithExamples(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleAddOpenAPIYamlWithSpecificResponsesWithSpecificationExamples() {
        // given
        String specUrlOrPayload = "org/mockserver/mock/openapi_petstore_example_with_examples.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of(
                "listPets", "500",
                "createPets", "default",
                "showPetById", "200"
            )
        );

        // then
        shouldBuildPetStoreExpectationsWithExamplesAndSpecificResponses(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldHandleInvalidOpenAPIJson() {
        try {
            // when
            new OpenAPIConverter(mockServerLogger).buildExpectations(
                "" +
                    "\"openapi\": \"3.0.0\"," + NEW_LINE +
                    "  \"info\": {" + NEW_LINE +
                    "    \"version\": \"1.0.0\"," + NEW_LINE +
                    "    \"title\": \"Swagger Petstore\"",
                null
            );

            // then
            fail("exception expected");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec from provided URL or payload because while parsing a block mapping" + NEW_LINE +
                " in 'reader', line 1, column 1:" + NEW_LINE +
                "    \"openapi\": \"3.0.0\"," + NEW_LINE +
                "    ^" + NEW_LINE +
                "expected <block end>, but found ','" + NEW_LINE +
                " in 'reader', line 1, column 19:" + NEW_LINE +
                "    \"openapi\": \"3.0.0\"," + NEW_LINE +
                "                      ^" + NEW_LINE +
                "" + NEW_LINE +
                " at [Source: (StringReader); line: 1, column: 19]"));
        }
    }

    @Test
    public void shouldHandleInvalidOpenAPIYaml() {
        try {
            // when
            new OpenAPIConverter(mockServerLogger).buildExpectations(
                FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.yaml").substring(0, 100),
                null
            );

            // then
            fail("exception expected");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec from provided URL or payload because while scanning a simple key" + NEW_LINE +
                " in 'reader', line 8, column 1:" + NEW_LINE +
                "    servers" + NEW_LINE +
                "    ^" + NEW_LINE +
                "could not find expected ':'" + NEW_LINE +
                " in 'reader', line 8, column 8:" + NEW_LINE +
                "    servers" + NEW_LINE +
                "           ^" + NEW_LINE +
                NEW_LINE +
                " at [Source: (StringReader); line: 8, column: 1]"));
        }
    }

    @Test
    public void shouldHandleInvalidOpenAPIJsonUrl() {
        try {
            // when
            new OpenAPIConverter(mockServerLogger).buildExpectations(
                "" +
                    "\"openapi\": \"3.0.0\"," + NEW_LINE +
                    "  \"info\": {" + NEW_LINE +
                    "    \"version\": \"1.0.0\"," + NEW_LINE +
                    "    \"title\": \"Swagger Petstore\"",
                null
            );

            // then
            fail("exception expected");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec from provided URL or payload because while parsing a block mapping" + NEW_LINE +
                " in 'reader', line 1, column 1:" + NEW_LINE +
                "    \"openapi\": \"3.0.0\"," + NEW_LINE +
                "    ^" + NEW_LINE +
                "expected <block end>, but found ','" + NEW_LINE +
                " in 'reader', line 1, column 19:" + NEW_LINE +
                "    \"openapi\": \"3.0.0\"," + NEW_LINE +
                "                      ^" + NEW_LINE +
                "" + NEW_LINE +
                " at [Source: (StringReader); line: 1, column: 19]"));
        }
    }

    @Test
    public void shouldHandleInvalidOpenAPIYamlUrl() {
        try {
            // when
            new OpenAPIConverter(mockServerLogger).buildExpectations(
                FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_petstore_example.yaml").substring(0, 100),
                null
            );

            // then
            fail("exception expected");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec from provided URL or payload because while scanning a simple key" + NEW_LINE +
                " in 'reader', line 8, column 1:" + NEW_LINE +
                "    servers" + NEW_LINE +
                "    ^" + NEW_LINE +
                "could not find expected ':'" + NEW_LINE +
                " in 'reader', line 8, column 8:" + NEW_LINE +
                "    servers" + NEW_LINE +
                "           ^" + NEW_LINE +
                NEW_LINE +
                " at [Source: (StringReader); line: 8, column: 1]"));
        }
    }

    private void shouldBuildPetStoreExpectations(String specUrlOrPayload, List<Expectation> actualExpectations) {
        assertThat(actualExpectations.size(), is(4));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "listPets")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("x-next", "some_string_value")
                        .withHeader("content-type", "application/json")
                        .withBody(json("[ {" + NEW_LINE +
                            "  \"id\" : 0," + NEW_LINE +
                            "  \"name\" : \"some_string_value\"," + NEW_LINE +
                            "  \"tag\" : \"some_string_value\"" + NEW_LINE +
                            "} ]"))
                )
        ));
        assertThat(actualExpectations.get(1), is(
            when(specUrlOrPayload, "createPets")
                .thenRespond(
                    response()
                        .withStatusCode(201)
                )
        ));
        assertThat(actualExpectations.get(2), is(
            when(specUrlOrPayload, "showPetById")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"id\" : 0," + NEW_LINE +
                            "  \"name\" : \"some_string_value\"," + NEW_LINE +
                            "  \"tag\" : \"some_string_value\"" + NEW_LINE +
                            "}"))
                )
        ));
        assertThat(actualExpectations.get(3), is(
            when(specUrlOrPayload, "somePath")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"id\" : 0," + NEW_LINE +
                            "  \"name\" : \"some_string_value\"," + NEW_LINE +
                            "  \"tag\" : \"some_string_value\"" + NEW_LINE +
                            "}"))
                )
        ));
    }

    private void shouldBuildPetStoreExpectationsWithSpecificResponses(String specUrlOrPayload, List<Expectation> actualExpectations) {
        assertThat(actualExpectations.size(), is(3));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "listPets")
                .thenRespond(
                    response()
                        .withStatusCode(500)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"code\" : 0," + NEW_LINE +
                            "  \"message\" : \"some_string_value\"" + NEW_LINE +
                            "}"))
                )
        ));
        assertThat(actualExpectations.get(1), is(
            when(specUrlOrPayload, "createPets")
                .thenRespond(
                    response()
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"code\" : 0," + NEW_LINE +
                            "  \"message\" : \"some_string_value\"" + NEW_LINE +
                            "}"))
                )
        ));
        assertThat(actualExpectations.get(2), is(
            when(specUrlOrPayload, "showPetById")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"id\" : 0," + NEW_LINE +
                            "  \"name\" : \"some_string_value\"," + NEW_LINE +
                            "  \"tag\" : \"some_string_value\"" + NEW_LINE +
                            "}"))
                )
        ));
    }

    private void shouldBuildPetStoreExpectationsWithExamples(String specUrlOrPayload, List<Expectation> actualExpectations) {
        assertThat(actualExpectations.size(), is(3));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "listPets")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("x-next", "/pets?query=752cd724e0d7&page=2")
                        .withHeader("content-type", "application/json")
                        .withBody(json("[ {" + NEW_LINE +
                            "  \"id\" : 1," + NEW_LINE +
                            "  \"name\" : \"Scruffles\"," + NEW_LINE +
                            "  \"tag\" : \"dog\"" + NEW_LINE +
                            "} ]"))
                )
        ));
        assertThat(actualExpectations.get(1), is(
            when(specUrlOrPayload, "createPets")
                .thenRespond(
                    response()
                        .withStatusCode(201)
                )
        ));
        assertThat(actualExpectations.get(2), is(
            when(specUrlOrPayload, "showPetById")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"id\" : 2," + NEW_LINE +
                            "  \"name\" : \"Crumble\"," + NEW_LINE +
                            "  \"tag\" : \"dog\"" + NEW_LINE +
                            "}"))
                )
        ));
    }

    private void shouldBuildPetStoreExpectationsWithExamplesAndSpecificResponses(String specUrlOrPayload, List<Expectation> actualExpectations) {
        assertThat(actualExpectations.size(), is(3));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "listPets")
                .thenRespond(
                    response()
                        .withStatusCode(500)
                        .withHeader("content-type", "application/json")
                        .withHeader("x-code","90")
                        .withBody(json("{" + NEW_LINE +
                            "  \"code\" : 0," + NEW_LINE +
                            "  \"message\" : \"some_string_value\"" + NEW_LINE +
                            "}"))
                )
        ));
        assertThat(actualExpectations.get(1), is(
            when(specUrlOrPayload, "createPets")
                .thenRespond(
                    response()
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"code\" : 0," + NEW_LINE +
                            "  \"message\" : \"some_string_value\"" + NEW_LINE +
                            "}"))
                )
        ));
        assertThat(actualExpectations.get(2), is(
            when(specUrlOrPayload, "showPetById")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"id\" : 2," + NEW_LINE +
                            "  \"name\" : \"Crumble\"," + NEW_LINE +
                            "  \"tag\" : \"dog\"" + NEW_LINE +
                            "}"))
                )
        ));
    }

}