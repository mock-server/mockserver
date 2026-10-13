package org.mockserver.openapi;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.mockserver.file.FilePath;
import org.mockserver.file.FileReader;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.AfterAction;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.OpenAPIDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.mock.Expectation.when;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

public class OpenAPIConverterTest {

    MockServerLogger mockServerLogger = new MockServerLogger(OpenAPIConverterTest.class);

    @Test
    public void shouldDetectXmlContentTypes() {
        // xml content types route a generated example through XmlExampleSerializer
        assertThat(OpenAPIConverter.isXmlContentType("application/xml"), is(true));
        assertThat(OpenAPIConverter.isXmlContentType("application/xml; charset=utf-8"), is(true));
        assertThat(OpenAPIConverter.isXmlContentType("text/xml"), is(true));
        assertThat(OpenAPIConverter.isXmlContentType("application/atom+xml"), is(true));
        assertThat(OpenAPIConverter.isXmlContentType("application/vnd.api+xml"), is(true));
        assertThat(OpenAPIConverter.isXmlContentType("APPLICATION/XML"), is(true));
        // non-xml content types are not treated as xml
        assertThat(OpenAPIConverter.isXmlContentType("application/json"), is(false));
        assertThat(OpenAPIConverter.isXmlContentType("application/vnd.api+json"), is(false));
        assertThat(OpenAPIConverter.isXmlContentType("text/plain"), is(false));
        assertThat(OpenAPIConverter.isXmlContentType(""), is(false));
        assertThat(OpenAPIConverter.isXmlContentType(null), is(false));
    }

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
            ImmutableMap.<String, Object>of(
                "listPets", "500",
                "createPets", "default",
                "showPetById", "200"
            )
        );

        // then
        shouldBuildPetStoreExpectationsWithSpecificResponses(specUrlOrPayload, actualExpectations);
    }

    @Test
    public void shouldBuildExpectationsForWebhooksOnlySpecWithoutNpe() {
        // given - a valid OAS 3.1 spec that omits paths entirely and declares only webhooks;
        // openAPI.getPaths() returns null and must not NPE (the NPE previously escaped the
        // control-plane PUT /mockserver/openapi handler as a server error)
        String specUrlOrPayload = "org/mockserver/openapi/openapi_31_webhooks_only.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            null
        );

        // then - the single webhook operation is generated as an expectation, no exception thrown
        assertThat(actualExpectations, hasSize(1));
        assertThat(((OpenAPIDefinition) actualExpectations.get(0).getHttpRequest()).getOperationId(), is("onNewPet"));
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
            ImmutableMap.<String, Object>of(
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
            ImmutableMap.<String, Object>of(
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
            ImmutableMap.<String, Object>of(
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
            ImmutableMap.<String, Object>of(
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
            ImmutableMap.<String, Object>of(
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
            ImmutableMap.<String, Object>of(
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
            ImmutableMap.<String, Object>of(
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
            assertThat(iae.getMessage(), is("Unable to load API spec, 1 problem found:" + NEW_LINE + NEW_LINE +
                "  syntax error (1):" + NEW_LINE +
                "    - line 1, column 19: while parsing a block mapping" + NEW_LINE +
                "       in 'reader', line 1, column 1:" + NEW_LINE +
                "          \"openapi\": \"3.0.0\"," + NEW_LINE +
                "          ^" + NEW_LINE +
                "      expected <block end>, but found ','" + NEW_LINE +
                "       in 'reader', line 1, column 19:" + NEW_LINE +
                "          \"openapi\": \"3.0.0\"," + NEW_LINE +
                "                            ^"));
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
            assertThat(iae.getMessage(), is("Unable to load API spec, 1 problem found:" + NEW_LINE + NEW_LINE +
                "  syntax error (1):" + NEW_LINE +
                "    - line 8, column 8: while scanning a simple key" + NEW_LINE +
                "       in 'reader', line 8, column 1:" + NEW_LINE +
                "          servers" + NEW_LINE +
                "          ^" + NEW_LINE +
                "      could not find expected ':'" + NEW_LINE +
                "       in 'reader', line 8, column 8:" + NEW_LINE +
                "          servers" + NEW_LINE +
                "                 ^"));
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
            assertThat(iae.getMessage(), is("Unable to load API spec, 1 problem found:" + NEW_LINE + NEW_LINE +
                "  syntax error (1):" + NEW_LINE +
                "    - line 1, column 19: while parsing a block mapping" + NEW_LINE +
                "       in 'reader', line 1, column 1:" + NEW_LINE +
                "          \"openapi\": \"3.0.0\"," + NEW_LINE +
                "          ^" + NEW_LINE +
                "      expected <block end>, but found ','" + NEW_LINE +
                "       in 'reader', line 1, column 19:" + NEW_LINE +
                "          \"openapi\": \"3.0.0\"," + NEW_LINE +
                "                            ^"));
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
            assertThat(iae.getMessage(), is("Unable to load API spec, 1 problem found:" + NEW_LINE + NEW_LINE +
                "  syntax error (1):" + NEW_LINE +
                "    - line 8, column 8: while scanning a simple key" + NEW_LINE +
                "       in 'reader', line 8, column 1:" + NEW_LINE +
                "          servers" + NEW_LINE +
                "          ^" + NEW_LINE +
                "      could not find expected ':'" + NEW_LINE +
                "       in 'reader', line 8, column 8:" + NEW_LINE +
                "          servers" + NEW_LINE +
                "                 ^"));
        }
    }

    @Test
    public void shouldResolveReusableExampleRefsInArrayExample() {
        // given - issue #1474 (array schema example with $ref items)
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_reusable_examples.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.<String, Object>of("listTasks", "200")
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
            ImmutableMap.<String, Object>of("getTask", "200")
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
            ImmutableMap.<String, Object>of("getTaskMediaTypeExample", "200")
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
            ImmutableMap.<String, Object>of("getTaskMediaTypeArrayExample", "200")
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
            ImmutableMap.<String, Object>of("getTaskNestedRef", "200")
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
            ImmutableMap.<String, Object>of("getTaskMixed", "200")
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
            ImmutableMap.<String, Object>of("getTaskDeepNested", "200")
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
        // given - issue #1474 (unresolvable $ref). The unresolved reference must be DROPPED (logged at
        // WARN), never leaked as a literal {"$ref": ...} node into the generated response body.
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_reusable_examples.yaml";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.<String, Object>of("getTaskUnresolvable", "200")
        );

        // then - the unresolvable $ref element is dropped (null) rather than leaking the literal $ref
        assertThat(actualExpectations.size(), is(1));
        assertThat(actualExpectations.get(0), is(
            when(specUrlOrPayload, "getTaskUnresolvable")
                .thenRespond(
                    response()
                        .withStatusCode(200)
                        .withHeader("content-type", "application/json")
                        .withBody(json("{" + NEW_LINE +
                            "  \"data\" : [ null ]" + NEW_LINE +
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
            ImmutableMap.<String, Object>of("getTaskDuplicateRef", "200")
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

    @Test
    public void shouldGenerateAfterActionsFromCallbacks() {
        // given
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_callbacks.json";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.<String, Object>of("createPetWithCallback", "201")
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        Expectation expectation = actualExpectations.get(0);
        assertThat(expectation.getAfterActions(), is(notNullValue()));
        assertThat(expectation.getAfterActions().size(), is(1));
        AfterAction afterAction = expectation.getAfterActions().get(0);
        HttpRequest callbackRequest = afterAction.getHttpRequest();
        assertThat(callbackRequest.getMethod().getValue(), is("POST"));
        assertThat(callbackRequest.getPath().getValue(), is("/pets/notify"));
        assertThat(callbackRequest.isSecure(), is(true));
        assertThat(callbackRequest.getFirstHeader("Host"), is("callback.example.com"));
        assertThat(callbackRequest.getFirstHeader("Content-Type"), is("application/json"));
        assertThat(callbackRequest.getBody().getValue().toString(), containsString("eventType"));
    }

    @Test
    public void shouldGenerateAfterActionsWithHttpUrlAndPort() {
        // given
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_callbacks.json";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.<String, Object>of("createOrderWithCallback", "201")
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        Expectation expectation = actualExpectations.get(0);
        assertThat(expectation.getAfterActions(), is(notNullValue()));
        assertThat(expectation.getAfterActions().size(), is(1));
        AfterAction afterAction = expectation.getAfterActions().get(0);
        HttpRequest callbackRequest = afterAction.getHttpRequest();
        assertThat(callbackRequest.getMethod().getValue(), is("PUT"));
        assertThat(callbackRequest.getPath().getValue(), is("/orders/status"));
        assertThat(callbackRequest.isSecure(), is(false));
        assertThat(callbackRequest.getFirstHeader("Host"), is("notifications.example.com:8080"));
    }

    @Test
    public void shouldGenerateAfterActionsWithRuntimeExpressionUrl() {
        // given
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_callbacks.json";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.<String, Object>of("createSubscriptionWithRuntimeCallback", "201")
        );

        // then - runtime expression is preserved verbatim for fire-time resolution
        assertThat(actualExpectations.size(), is(1));
        Expectation expectation = actualExpectations.get(0);
        assertThat(expectation.getAfterActions(), is(notNullValue()));
        assertThat(expectation.getAfterActions().size(), is(1));
        AfterAction afterAction = expectation.getAfterActions().get(0);
        HttpRequest callbackRequest = afterAction.getHttpRequest();
        assertThat(callbackRequest.getMethod().getValue(), is("POST"));
        assertThat(callbackRequest.getPath().getValue(), is("{$request.body#/callbackUrl}/events"));
    }

    @Test
    public void shouldNotGenerateAfterActionsWhenNoCallbacks() {
        // given
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_callbacks.json";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.<String, Object>of("listItemsNoCallback", "200")
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        Expectation expectation = actualExpectations.get(0);
        assertThat(expectation.getAfterActions(), is(nullValue()));
    }

    @Test
    public void shouldGenerateMultipleAfterActionsFromMultipleCallbacks() {
        // given
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_callbacks.json";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.<String, Object>of("registerMultipleCallbacks", "201")
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        Expectation expectation = actualExpectations.get(0);
        assertThat(expectation.getAfterActions(), is(notNullValue()));
        assertThat(expectation.getAfterActions().size(), is(2));
    }

    @Test
    public void shouldGenerateAfterActionsWithRelativePath() {
        // given
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example_with_callbacks.json";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.<String, Object>of("relativeCallback", "201")
        );

        // then
        assertThat(actualExpectations.size(), is(1));
        Expectation expectation = actualExpectations.get(0);
        assertThat(expectation.getAfterActions(), is(notNullValue()));
        assertThat(expectation.getAfterActions().size(), is(1));
        AfterAction afterAction = expectation.getAfterActions().get(0);
        HttpRequest callbackRequest = afterAction.getHttpRequest();
        assertThat(callbackRequest.getMethod().getValue(), is("POST"));
        assertThat(callbackRequest.getPath().getValue(), is("/callback/done"));
    }

    @Test
    public void shouldNotAffectExistingSpecsWithoutCallbacks() {
        // given
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example.json";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            null
        );

        // then
        shouldBuildPetStoreExpectations(specUrlOrPayload, actualExpectations);
        for (Expectation expectation : actualExpectations) {
            assertThat(expectation.getAfterActions(), is(nullValue()));
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

    // ---- stable id generation tests ----

    @Test
    public void shouldGenerateStableIdsFromSpecTitle() {
        // given - petstore spec has title "Swagger Petstore"
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example.json";

        // when
        List<Expectation> expectations = new OpenAPIConverter(mockServerLogger).buildExpectations(specUrlOrPayload, null);

        // then - ids should be openapi:swagger_petstore_<hash>:<operationId>
        // the key embeds a short hash of the spec source identity to avoid cross-spec collisions
        String prefix = "openapi:" + OpenApiSyncPlanner.deriveSpecKey("Swagger Petstore", specUrlOrPayload) + ":";
        assertThat(prefix, startsWith("openapi:swagger_petstore_"));
        assertThat(expectations.get(0).getId(), is(prefix + "listPets"));
        assertThat(expectations.get(1).getId(), is(prefix + "createPets"));
        assertThat(expectations.get(2).getId(), is(prefix + "showPetById"));
        assertThat(expectations.get(3).getId(), is(prefix + "somePath"));
    }

    @Test
    public void shouldGenerateIdempotentIdsOnReImport() {
        // given
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example.json";
        OpenAPIConverter converter = new OpenAPIConverter(mockServerLogger);

        // when - build expectations twice from the same spec
        List<Expectation> first = converter.buildExpectations(specUrlOrPayload, null);
        List<Expectation> second = converter.buildExpectations(specUrlOrPayload, null);

        // then - ids should be identical across both invocations
        assertThat(first.size(), is(second.size()));
        for (int i = 0; i < first.size(); i++) {
            assertThat("id at index " + i, second.get(i).getId(), is(first.get(i).getId()));
        }
    }

    @Test
    public void shouldGenerateStableIdsForSimpleSpec() {
        // given - simple spec has title "Simple OpenAPI"
        String specUrlOrPayload = "org/mockserver/openapi/openapi_simple_example.json";

        // when
        List<Expectation> expectations = new OpenAPIConverter(mockServerLogger).buildExpectations(specUrlOrPayload, null);

        // then
        assertThat(expectations.size(), is(1));
        String prefix = "openapi:" + OpenApiSyncPlanner.deriveSpecKey("Simple OpenAPI", specUrlOrPayload) + ":";
        assertThat(prefix, startsWith("openapi:simple_openapi_"));
        assertThat(expectations.get(0).getId(), is(prefix + "listPets"));
    }

    @Test
    public void shouldGenerateStableIdsWithSpecificResponses() {
        // given
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example.json";

        // when
        List<Expectation> expectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.<String, Object>of(
                "listPets", "500",
                "createPets", "default",
                "showPetById", "200"
            )
        );

        // then - ids should be based on spec key (title + source hash) + operationId, regardless of response selection
        String prefix = "openapi:" + OpenApiSyncPlanner.deriveSpecKey("Swagger Petstore", specUrlOrPayload) + ":";
        assertThat(expectations.get(0).getId(), is(prefix + "listPets"));
        assertThat(expectations.get(1).getId(), is(prefix + "createPets"));
        assertThat(expectations.get(2).getId(), is(prefix + "showPetById"));
    }

    @Test
    public void shouldGenerateStableIdsFromInlineSpecWithTitle() {
        // given - inline YAML spec with a title
        String specUrlOrPayload =
            "openapi: 3.0.0\n" +
            "info:\n" +
            "  title: My Inline API\n" +
            "  version: 1.0.0\n" +
            "paths:\n" +
            "  /hello:\n" +
            "    get:\n" +
            "      operationId: sayHello\n" +
            "      responses:\n" +
            "        '200':\n" +
            "          description: OK\n";

        // when
        List<Expectation> expectations = new OpenAPIConverter(mockServerLogger).buildExpectations(specUrlOrPayload, null);

        // then
        assertThat(expectations.size(), is(1));
        String prefix = "openapi:" + OpenApiSyncPlanner.deriveSpecKey("My Inline API", specUrlOrPayload) + ":";
        assertThat(prefix, startsWith("openapi:my_inline_api_"));
        assertThat(expectations.get(0).getId(), is(prefix + "sayHello"));
    }

    @Test
    public void shouldGenerateDistinctSpecKeysForDifferentSpecsWithSameTitle() {
        // given - two DIFFERENT inline specs that share the same info.title "Shared Title"
        String specA =
            "openapi: 3.0.0\n" +
            "info:\n" +
            "  title: Shared Title\n" +
            "  version: 1.0.0\n" +
            "paths:\n" +
            "  /a:\n" +
            "    get:\n" +
            "      operationId: opA\n" +
            "      responses:\n" +
            "        '200':\n" +
            "          description: OK\n";
        String specB =
            "openapi: 3.0.0\n" +
            "info:\n" +
            "  title: Shared Title\n" +
            "  version: 1.0.0\n" +
            "paths:\n" +
            "  /b:\n" +
            "    get:\n" +
            "      operationId: opB\n" +
            "      responses:\n" +
            "        '200':\n" +
            "          description: OK\n";

        // when
        OpenAPIConverter converter = new OpenAPIConverter(mockServerLogger);
        List<Expectation> expectationsA = converter.buildExpectations(specA, null);
        List<Expectation> expectationsB = converter.buildExpectations(specB, null);

        // then - both sanitize to "shared_title" but the source hash differs, so the
        // namespaces are distinct (no cross-spec collision -> no cross-spec data loss)
        String idA = expectationsA.get(0).getId();
        String idB = expectationsB.get(0).getId();
        assertThat(idA, startsWith("openapi:shared_title_"));
        assertThat(idB, startsWith("openapi:shared_title_"));
        String prefixA = idA.substring(0, idA.lastIndexOf(':') + 1);
        String prefixB = idB.substring(0, idB.lastIndexOf(':') + 1);
        assertThat("distinct specs with the same title must get distinct namespaces", prefixA, is(not(prefixB)));
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

    @Test
    public void shouldBuildExpectationForStatusCodeRangeKey() {
        // given - spec defines responses keyed only by the range bucket "2XX" (legal per OpenAPI 3.x)
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_status_code_range.yaml");

        // when - must NOT throw NumberFormatException for the "2XX" key
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.<String, Object>of("rangeOnly", "2XX")
        );

        // then - the range key "2XX" resolves to status code 200
        assertThat(actualExpectations, hasSize(1));
        assertThat(actualExpectations.get(0).getHttpResponse().getStatusCode(), is(200));
    }

    @Test
    public void shouldNotThrowWhenBuildingAllExpectationsForSpecWithRangeKeys() {
        // given - a spec whose operations use range status-code keys throughout
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_status_code_range.yaml");

        // when - building every expectation (operationsAndResponses == null) must not throw
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            null
        );

        // then - "2XX" -> 200 and "4XX" -> 400 (exactAndRange prefers exact "200")
        assertThat(actualExpectations, hasSize(3));
        Integer rangeOnlyStatus = actualExpectations.stream()
            .filter(e -> "rangeOnly".equals(((OpenAPIDefinition) e.getHttpRequest()).getOperationId()))
            .findFirst().orElseThrow(AssertionError::new)
            .getHttpResponse().getStatusCode();
        assertThat(rangeOnlyStatus, is(200));
        Integer notFoundStatus = actualExpectations.stream()
            .filter(e -> "notFoundRange".equals(((OpenAPIDefinition) e.getHttpRequest()).getOperationId()))
            .findFirst().orElseThrow(AssertionError::new)
            .getHttpResponse().getStatusCode();
        assertThat(notFoundStatus, is(400));
    }

    @Test
    public void shouldStillHandleNumericAndDefaultStatusCodeKeys() {
        // given - regression: a spec mixing a numeric "200" and a "default" key behaves as before
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_petstore_example.json");

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(
            specUrlOrPayload,
            ImmutableMap.<String, Object>of(
                "showPetById", "200",
                "createPets", "default"
            )
        );

        // then - "200" sets status 200, "default" leaves the status unset (null), exactly as before this fix
        Integer showPetByIdStatus = actualExpectations.stream()
            .filter(e -> "showPetById".equals(((OpenAPIDefinition) e.getHttpRequest()).getOperationId()))
            .findFirst().orElseThrow(AssertionError::new)
            .getHttpResponse().getStatusCode();
        assertThat(showPetByIdStatus, is(200));
        Integer createPetsStatus = actualExpectations.stream()
            .filter(e -> "createPets".equals(((OpenAPIDefinition) e.getHttpRequest()).getOperationId()))
            .findFirst().orElseThrow(AssertionError::new)
            .getHttpResponse().getStatusCode();
        assertThat(createPetsStatus, is(nullValue()));
    }

    @Test
    public void shouldGenerateDistinctElementNamesForRecursiveXmlSchema() {
        // a recursive XML schema (Node{left:$ref Node, right:$ref Node}) reuses one cached Example for both
        // properties; the XML body must render BOTH <left> and <right>, not rename the shared instance so
        // one property is dropped/duplicated
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_recursive_xml.yaml");

        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(specUrlOrPayload, null);

        String body = actualExpectations.stream()
            .filter(e -> "getNode".equals(((OpenAPIDefinition) e.getHttpRequest()).getOperationId()))
            .findFirst().orElseThrow(AssertionError::new)
            .getHttpResponse().getBodyAsString();
        assertThat("recursive XML must keep the 'left' element", body, containsString("<left>"));
        assertThat("recursive XML must keep the 'right' element (shared Example not renamed)", body, containsString("<right>"));
    }

    @Test
    public void shouldNameXmlArrayItemsAfterTheArrayPropertyNotTheLiteralArray() {
        // arrays whose items have no items.xml.name must render each item under the array PROPERTY name
        // (tags -> <tags>, animals -> <animals>), never the literal element name <array>
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_xml_arrays.yaml");

        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(specUrlOrPayload, null);

        String body = actualExpectations.stream()
            .filter(e -> "getZoo".equals(((OpenAPIDefinition) e.getHttpRequest()).getOperationId()))
            .findFirst().orElseThrow(AssertionError::new)
            .getHttpResponse().getBodyAsString();
        assertThat("array of scalars renders items under the property name", body, containsString("<tags>"));
        assertThat("array of objects renders items under the property name", body, containsString("<animals>"));
        assertThat("array items must NOT be named after the literal type 'array'", body, not(containsString("<array>")));
    }

    @Test
    public void shouldNameXmlElementsAfterPropertiesNotSchemaRefForRecursiveSchema() {
        // a recursive $ref (Tree{children: array of $ref Tree}) cannot be inlined by the parser, so it
        // hits the ExampleBuilder $ref-resolution path. The child array items must render under the
        // PROPERTY name (<children>), never the schema component name (<Tree>).
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_recursive_xml_ref.yaml");

        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(specUrlOrPayload, null);

        String body = actualExpectations.stream()
            .filter(e -> "getTree".equals(((OpenAPIDefinition) e.getHttpRequest()).getOperationId()))
            .findFirst().orElseThrow(AssertionError::new)
            .getHttpResponse().getBodyAsString();
        assertThat("recursive array $ref item renders under the property name", body, containsString("<children>"));
        assertThat("must NOT render the schema component name as an element", body, not(containsString("<Tree>")));
    }

    @Test
    public void shouldRenderXmlAttributeDeclaredAfterElementProperty() {
        // an object whose element property (name) is declared BEFORE its attribute property (id,
        // xml.attribute: true). StAX requires attributes be written immediately after the start
        // element and before any child element — so the serializer must write attribute children
        // first regardless of declaration order, otherwise it throws "Attribute not associated with
        // any element", the write is aborted and the whole response body comes back null/empty.
        String specUrlOrPayload = FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_xml_attribute_ordering.yaml");

        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(specUrlOrPayload, null);

        String body = actualExpectations.stream()
            .filter(e -> "getTag".equals(((OpenAPIDefinition) e.getHttpRequest()).getOperationId()))
            .findFirst().orElseThrow(AssertionError::new)
            .getHttpResponse().getBodyAsString();
        assertThat("attribute-after-element must not abort the write to an empty body", body, is(notNullValue()));
        assertThat("body must carry the wrapping element", body, containsString("<Tag"));
        assertThat("the attribute must be rendered on the element", body, containsString("id=\""));
        assertThat("the element child must still be present", body, containsString("<name>"));
    }

    // ---- example request generation (path / query / header / cookie parameters) ----

    @Test
    public void shouldGenerateExampleRequestsWithParameterValues() {
        // given - the parameters spec declares a path (itemId), required query (filter), required
        // header (X-Trace-Id), and required cookie (session) parameter on operation getItem
        String specUrlOrPayload = "org/mockserver/openapi/openapi_parameters_example.yaml";

        // when
        Map<String, HttpRequest> exampleRequests = new OpenAPIConverter(mockServerLogger).buildExampleRequests(specUrlOrPayload);

        // then - the generated example request carries concrete example values for every declared
        // path/query/header/cookie parameter
        assertThat(exampleRequests.keySet(), hasItem("getItem"));
        HttpRequest getItem = exampleRequests.get("getItem");
        assertThat(getItem.getMethod().getValue(), is("GET"));
        // path parameter substituted (itemId schema is integer -> "0")
        assertThat(getItem.getPath().getValue(), is("/items/0"));
        // required query parameter present
        assertThat(getItem.getFirstQueryStringParameter("filter"), is(not(emptyString())));
        // required header parameter present
        assertThat(getItem.getFirstHeader("X-Trace-Id"), is(not(emptyString())));
        // required cookie parameter present
        assertThat(getItem.getCookieList().stream().anyMatch(c -> "session".equals(c.getName().getValue())), is(true));
    }

    @Test
    public void shouldGenerateExampleRequestWithBodyForOperationWithRequestBody() {
        // given - petstore createPets declares a required JSON request body
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example.json";

        // when
        Map<String, HttpRequest> exampleRequests = new OpenAPIConverter(mockServerLogger).buildExampleRequests(specUrlOrPayload);

        // then - createPets carries a content-type header and a generated example body
        HttpRequest createPets = exampleRequests.get("createPets");
        assertThat(createPets, is(notNullValue()));
        assertThat(createPets.getMethod().getValue(), is("POST"));
        assertThat(createPets.getFirstHeader("content-type"), is("application/json"));
        assertThat(createPets.getBodyAsString(), containsString("name"));
    }

    @Test
    public void shouldGenerateExampleRequestsWithoutParametersForParameterlessOperation() {
        // given - the simple spec's listPets operation declares no parameters
        String specUrlOrPayload = "org/mockserver/openapi/openapi_simple_example.json";

        // when
        Map<String, HttpRequest> exampleRequests = new OpenAPIConverter(mockServerLogger).buildExampleRequests(specUrlOrPayload);

        // then - the example request carries no query parameters, headers, or cookies beyond the path
        HttpRequest listPets = exampleRequests.get("listPets");
        assertThat(listPets, is(notNullValue()));
        assertThat(listPets.getQueryStringParameterList() == null || listPets.getQueryStringParameterList().isEmpty(), is(true));
        assertThat(listPets.getHeaderList() == null || listPets.getHeaderList().isEmpty(), is(true));
        assertThat(listPets.getCookieList() == null || listPets.getCookieList().isEmpty(), is(true));
    }

    @Test
    public void shouldNotChangeBuildExpectationsOutputForSpecsWithoutParameters() {
        // given - the additive buildExampleRequests must not regress the existing buildExpectations
        // output; the petstore expectations remain an OpenAPIDefinition matcher with the same response
        String specUrlOrPayload = "org/mockserver/openapi/openapi_petstore_example.json";

        // when
        List<Expectation> actualExpectations = new OpenAPIConverter(mockServerLogger).buildExpectations(specUrlOrPayload, null);

        // then
        shouldBuildPetStoreExpectations(specUrlOrPayload, actualExpectations);
    }

}