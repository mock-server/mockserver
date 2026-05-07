package org.mockserver.openapi;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.mockserver.file.FilePath;
import org.mockserver.file.FileReader;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;

import java.util.List;

import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.mock.Expectation.when;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

public class OpenAPIConverterTest {

    MockServerLogger mockServerLogger = new MockServerLogger(OpenAPIConverterTest.class);

    @Test
    public void shouldHandleAddOpenAPIJson() {
        // given
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json");

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
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json");

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
    public void shouldHandleAddOpenAPIJsonWithCircularReferences() {
        // given
        String specUrlOrPayload = "org/mockserver/openapi/openapi_circular_reference_example.json";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            null
        );

        // then
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
                            "  \"tag\" : \"some_string_value\"," + NEW_LINE +
                            "  \"accessories\" : [ {" + NEW_LINE +
                            "    \"id\" : 0," + NEW_LINE +
                            "    \"name\" : \"some_string_value\"," + NEW_LINE +
                            "    \"pet\" : {" + NEW_LINE +
                            "      \"id\" : 0," + NEW_LINE +
                            "      \"name\" : \"some_string_value\"," + NEW_LINE +
                            "      \"tag\" : \"some_string_value\"," + NEW_LINE +
                            "      \"accessories\" : [ {" + NEW_LINE +
                            "        \"id\" : 0," + NEW_LINE +
                            "        \"name\" : \"some_string_value\"" + NEW_LINE +
                            "      } ]" + NEW_LINE +
                            "    }" + NEW_LINE +
                            "  } ]" + NEW_LINE +
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
                            "  \"tag\" : \"some_string_value\"," + NEW_LINE +
                            "  \"accessories\" : [ {" + NEW_LINE +
                            "    \"id\" : 0," + NEW_LINE +
                            "    \"name\" : \"some_string_value\"," + NEW_LINE +
                            "    \"pet\" : {" + NEW_LINE +
                            "      \"id\" : 0," + NEW_LINE +
                            "      \"name\" : \"some_string_value\"," + NEW_LINE +
                            "      \"tag\" : \"some_string_value\"," + NEW_LINE +
                            "      \"accessories\" : [ {" + NEW_LINE +
                            "        \"id\" : 0," + NEW_LINE +
                            "        \"name\" : \"some_string_value\"" + NEW_LINE +
                            "      } ]" + NEW_LINE +
                            "    }" + NEW_LINE +
                            "  } ]" + NEW_LINE +
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
                            "  \"tag\" : \"some_string_value\"," + NEW_LINE +
                            "  \"accessories\" : [ {" + NEW_LINE +
                            "    \"id\" : 0," + NEW_LINE +
                            "    \"name\" : \"some_string_value\"," + NEW_LINE +
                            "    \"pet\" : {" + NEW_LINE +
                            "      \"id\" : 0," + NEW_LINE +
                            "      \"name\" : \"some_string_value\"," + NEW_LINE +
                            "      \"tag\" : \"some_string_value\"," + NEW_LINE +
                            "      \"accessories\" : [ {" + NEW_LINE +
                            "        \"id\" : 0," + NEW_LINE +
                            "        \"name\" : \"some_string_value\"" + NEW_LINE +
                            "      } ]" + NEW_LINE +
                            "    }" + NEW_LINE +
                            "  } ]" + NEW_LINE +
                            "}"))
                )
        ));
    }

    @Test
    public void shouldHandleAddOpenAPIYaml() {
        // given
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.yaml");

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
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.yaml");

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
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example.json";

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
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example.json";

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
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example.yaml";

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
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example.yaml";

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
        String specUrlOrPayload = FilePath.getURL("org/mockserver/openapi/openapi_petstore_example.json").toString();

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
        String specUrlOrPayload = FilePath.getURL("org/mockserver/openapi/openapi_petstore_example.json").toString();

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
        String specUrlOrPayload = FilePath.getURL("org/mockserver/openapi/openapi_petstore_example.yaml").toString();

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
        String specUrlOrPayload = FilePath.getURL("org/mockserver/openapi/openapi_petstore_example.yaml").toString();

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
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_examples.json";

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
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_examples.json";

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
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_examples.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            null
        );

        // then
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
                            "}, {" + NEW_LINE +
                            "  \"id\" : 2," + NEW_LINE +
                            "  \"name\" : \"Goldie\"," + NEW_LINE +
                            "  \"tag\" : \"fish\"" + NEW_LINE +
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

    @Test
    public void shouldHandleAddOpenAPIYamlWithSpecificResponsesWithSpecificationExamples() {
        // given
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_examples.yaml";

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
    public void shouldHandleAddOpenAPIYamlWithJsonStringExample() {
        // given
        String specUrlOrPayload = "org/mockserver/openapi/openapi_with_json_string_example.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            null
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "GET /test")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("\"06be83b3-5fb5-4103-a9b3-3fcd097a0634\""))
                )
        ));
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
            assertThat(iae.getMessage(), is("Unable to load API spec, while parsing a block mapping" + NEW_LINE +
                " in 'reader', line 1, column 1:" + NEW_LINE +
                "    \"openapi\": \"3.0.0\"," + NEW_LINE +
                "    ^" + NEW_LINE +
                "expected <block end>, but found ','" + NEW_LINE +
                " in 'reader', line 1, column 19:" + NEW_LINE +
                "    \"openapi\": \"3.0.0\"," + NEW_LINE +
                "                      ^"));
        }
    }

    @Test
    public void shouldHandleInvalidOpenAPIYaml() {
        try {
            // when
            new OpenAPIConverter(mockServerLogger).buildExpectations(
                FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.yaml").substring(0, 100),
                null
            );

            // then
            fail("exception expected");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec, while scanning a simple key" + NEW_LINE +
                " in 'reader', line 8, column 1:" + NEW_LINE +
                "    servers" + NEW_LINE +
                "    ^" + NEW_LINE +
                "could not find expected ':'" + NEW_LINE +
                " in 'reader', line 8, column 8:" + NEW_LINE +
                "    servers" + NEW_LINE +
                "           ^"));
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
            assertThat(iae.getMessage(), is("Unable to load API spec, while parsing a block mapping" + NEW_LINE +
                " in 'reader', line 1, column 1:" + NEW_LINE +
                "    \"openapi\": \"3.0.0\"," + NEW_LINE +
                "    ^" + NEW_LINE +
                "expected <block end>, but found ','" + NEW_LINE +
                " in 'reader', line 1, column 19:" + NEW_LINE +
                "    \"openapi\": \"3.0.0\"," + NEW_LINE +
                "                      ^"));
        }
    }

    @Test
    public void shouldHandleInvalidOpenAPIYamlUrl() {
        try {
            // when
            new OpenAPIConverter(mockServerLogger).buildExpectations(
                FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.yaml").substring(0, 100),
                null
            );

            // then
            fail("exception expected");
        } catch (IllegalArgumentException iae) {
            assertThat(iae.getMessage(), is("Unable to load API spec, while scanning a simple key" + NEW_LINE +
                " in 'reader', line 8, column 1:" + NEW_LINE +
                "    servers" + NEW_LINE +
                "    ^" + NEW_LINE +
                "could not find expected ':'" + NEW_LINE +
                " in 'reader', line 8, column 8:" + NEW_LINE +
                "    servers" + NEW_LINE +
                "           ^"));
        }
    }

    @Test
    public void shouldResolveReusableExampleRefsInArrayExample() {
        // given - issue #1474 (array schema example with $ref items)
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_reusable_examples.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of("listTasks", "200")
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "listTasks")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"data\" : [ {" + NEW_LINE +
                            "    \"attributes\" : {" + NEW_LINE +
                            "      \"taskStatus\" : {" + NEW_LINE +
                            "        \"code\" : 2006," + NEW_LINE +
                            "        \"description\" : \"Pending\"" + NEW_LINE +
                            "      }," + NEW_LINE +
                            "      \"createdTime\" : \"2019-10-10T20:20:20Z\"," + NEW_LINE +
                            "      \"lastUpdatedTime\" : \"2019-10-11T20:20:20Z\"" + NEW_LINE +
                            "    }" + NEW_LINE +
                            "  }, {" + NEW_LINE +
                            "    \"attributes\" : {" + NEW_LINE +
                            "      \"taskStatus\" : {" + NEW_LINE +
                            "        \"code\" : 1000," + NEW_LINE +
                            "        \"description\" : \"Completed\"" + NEW_LINE +
                            "      }," + NEW_LINE +
                            "      \"createdTime\" : \"2019-10-10T20:20:20Z\"," + NEW_LINE +
                            "      \"lastUpdatedTime\" : \"2019-10-11T21:20:20Z\"" + NEW_LINE +
                            "    }" + NEW_LINE +
                            "  } ]" + NEW_LINE +
                            "}"))
                )
        ));
    }

    @Test
    public void shouldResolveReusableExampleRefInSchemaExample() {
        // given - issue #1474 (inline schema example as single $ref)
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_reusable_examples.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of("getTask", "200")
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "getTask")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"data\" : {" + NEW_LINE +
                            "    \"attributes\" : {" + NEW_LINE +
                            "      \"taskStatus\" : {" + NEW_LINE +
                            "        \"code\" : 2006," + NEW_LINE +
                            "        \"description\" : \"Pending\"" + NEW_LINE +
                            "      }," + NEW_LINE +
                            "      \"createdTime\" : \"2019-10-10T20:20:20Z\"," + NEW_LINE +
                            "      \"lastUpdatedTime\" : \"2019-10-11T20:20:20Z\"" + NEW_LINE +
                            "    }" + NEW_LINE +
                            "  }" + NEW_LINE +
                            "}"))
                )
        ));
    }

    @Test
    public void shouldResolveReusableExampleRefInMediaTypeExample() {
        // given - issue #1474 (media type example with single $ref)
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_reusable_examples.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of("getTaskMediaTypeExample", "200")
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "getTaskMediaTypeExample")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"attributes\" : {" + NEW_LINE +
                            "    \"taskStatus\" : {" + NEW_LINE +
                            "      \"code\" : 2006," + NEW_LINE +
                            "      \"description\" : \"Pending\"" + NEW_LINE +
                            "    }," + NEW_LINE +
                            "    \"createdTime\" : \"2019-10-10T20:20:20Z\"," + NEW_LINE +
                            "    \"lastUpdatedTime\" : \"2019-10-11T20:20:20Z\"" + NEW_LINE +
                            "  }" + NEW_LINE +
                            "}"))
                )
        ));
    }

    @Test
    public void shouldResolveReusableExampleRefsInMediaTypeArrayExample() {
        // given - issue #1474 (media type example with $ref items in array)
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_reusable_examples.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of("getTaskMediaTypeArrayExample", "200")
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "getTaskMediaTypeArrayExample")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("[ {" + NEW_LINE +
                            "  \"attributes\" : {" + NEW_LINE +
                            "    \"taskStatus\" : {" + NEW_LINE +
                            "      \"code\" : 2006," + NEW_LINE +
                            "      \"description\" : \"Pending\"" + NEW_LINE +
                            "    }," + NEW_LINE +
                            "    \"createdTime\" : \"2019-10-10T20:20:20Z\"," + NEW_LINE +
                            "    \"lastUpdatedTime\" : \"2019-10-11T20:20:20Z\"" + NEW_LINE +
                            "  }" + NEW_LINE +
                            "}, {" + NEW_LINE +
                            "  \"attributes\" : {" + NEW_LINE +
                            "    \"taskStatus\" : {" + NEW_LINE +
                            "      \"code\" : 1000," + NEW_LINE +
                            "      \"description\" : \"Completed\"" + NEW_LINE +
                            "    }," + NEW_LINE +
                            "    \"createdTime\" : \"2019-10-10T20:20:20Z\"," + NEW_LINE +
                            "    \"lastUpdatedTime\" : \"2019-10-11T21:20:20Z\"" + NEW_LINE +
                            "  }" + NEW_LINE +
                            "} ]"))
                )
        ));
    }

    @Test
    public void shouldResolveNestedReusableExampleRef() {
        // given - issue #1474 (nested $ref inside example object)
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_reusable_examples.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of("getTaskNestedRef", "200")
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "getTaskNestedRef")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"wrapper\" : {" + NEW_LINE +
                            "    \"inner\" : {" + NEW_LINE +
                            "      \"attributes\" : {" + NEW_LINE +
                            "        \"taskStatus\" : {" + NEW_LINE +
                            "          \"code\" : 2006," + NEW_LINE +
                            "          \"description\" : \"Pending\"" + NEW_LINE +
                            "        }," + NEW_LINE +
                            "        \"createdTime\" : \"2019-10-10T20:20:20Z\"," + NEW_LINE +
                            "        \"lastUpdatedTime\" : \"2019-10-11T20:20:20Z\"" + NEW_LINE +
                            "      }" + NEW_LINE +
                            "    }" + NEW_LINE +
                            "  }" + NEW_LINE +
                            "}"))
                )
        ));
    }

    @Test
    public void shouldResolveMixedInlineAndReusableExampleRefs() {
        // given - issue #1474 (mixed inline data and $ref in same array example)
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_reusable_examples.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of("getTaskMixed", "200")
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "getTaskMixed")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"data\" : [ {" + NEW_LINE +
                            "    \"attributes\" : {" + NEW_LINE +
                            "      \"taskStatus\" : {" + NEW_LINE +
                            "        \"code\" : 9999," + NEW_LINE +
                            "        \"description\" : \"Inline\"" + NEW_LINE +
                            "      }," + NEW_LINE +
                            "      \"createdTime\" : \"2020-01-01T00:00:00Z\"," + NEW_LINE +
                            "      \"lastUpdatedTime\" : \"2020-01-02T00:00:00Z\"" + NEW_LINE +
                            "    }" + NEW_LINE +
                            "  }, {" + NEW_LINE +
                            "    \"attributes\" : {" + NEW_LINE +
                            "      \"taskStatus\" : {" + NEW_LINE +
                            "        \"code\" : 1000," + NEW_LINE +
                            "        \"description\" : \"Completed\"" + NEW_LINE +
                            "      }," + NEW_LINE +
                            "      \"createdTime\" : \"2019-10-10T20:20:20Z\"," + NEW_LINE +
                            "      \"lastUpdatedTime\" : \"2019-10-11T21:20:20Z\"" + NEW_LINE +
                            "    }" + NEW_LINE +
                            "  } ]" + NEW_LINE +
                            "}"))
                )
        ));
    }

    @Test
    public void shouldResolveDeeplyNestedReusableExampleRef() {
        // given - issue #1474 (deeply nested $ref several levels deep)
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_reusable_examples.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of("getTaskDeepNested", "200")
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "getTaskDeepNested")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"level1\" : {" + NEW_LINE +
                            "    \"level2\" : {" + NEW_LINE +
                            "      \"items\" : [ {" + NEW_LINE +
                            "        \"attributes\" : {" + NEW_LINE +
                            "          \"taskStatus\" : {" + NEW_LINE +
                            "            \"code\" : 2006," + NEW_LINE +
                            "            \"description\" : \"Pending\"" + NEW_LINE +
                            "          }," + NEW_LINE +
                            "          \"createdTime\" : \"2019-10-10T20:20:20Z\"," + NEW_LINE +
                            "          \"lastUpdatedTime\" : \"2019-10-11T20:20:20Z\"" + NEW_LINE +
                            "        }" + NEW_LINE +
                            "      } ]" + NEW_LINE +
                            "    }" + NEW_LINE +
                            "  }" + NEW_LINE +
                            "}"))
                )
        ));
    }

    @Test
    public void shouldHandleUnresolvableExampleRef() {
        // given - issue #1474 (unresolvable $ref returns literal $ref object)
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_reusable_examples.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of("getTaskUnresolvable", "200")
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "getTaskUnresolvable")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"data\" : [ {" + NEW_LINE +
                            "    \"$ref\" : \"#/components/examples/nonExistentExample/value\"" + NEW_LINE +
                            "  } ]" + NEW_LINE +
                            "}"))
                )
        ));
    }

    @Test
    public void shouldResolveDuplicateRefsInSameExample() {
        // given - issue #1474 (same $ref used multiple times resolves all occurrences)
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_reusable_examples.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.of("getTaskDuplicateRef", "200")
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "getTaskDuplicateRef")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"tasks\" : [ {" + NEW_LINE +
                            "    \"attributes\" : {" + NEW_LINE +
                            "      \"taskStatus\" : {" + NEW_LINE +
                            "        \"code\" : 2006," + NEW_LINE +
                            "        \"description\" : \"Pending\"" + NEW_LINE +
                            "      }," + NEW_LINE +
                            "      \"createdTime\" : \"2019-10-10T20:20:20Z\"," + NEW_LINE +
                            "      \"lastUpdatedTime\" : \"2019-10-11T20:20:20Z\"" + NEW_LINE +
                            "    }" + NEW_LINE +
                            "  }, {" + NEW_LINE +
                            "    \"attributes\" : {" + NEW_LINE +
                            "      \"taskStatus\" : {" + NEW_LINE +
                            "        \"code\" : 2006," + NEW_LINE +
                            "        \"description\" : \"Pending\"" + NEW_LINE +
                            "      }," + NEW_LINE +
                            "      \"createdTime\" : \"2019-10-10T20:20:20Z\"," + NEW_LINE +
                            "      \"lastUpdatedTime\" : \"2019-10-11T20:20:20Z\"" + NEW_LINE +
                            "    }" + NEW_LINE +
                            "  } ]" + NEW_LINE +
                            "}"))
                )
        ));
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
                        .withHeader("x-code", "90")
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