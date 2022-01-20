package org.mockserver.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.file.FileReader;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.OpenAPIExpectation;
import org.mockserver.serialization.model.OpenAPIExpectationDTO;

import java.util.Arrays;

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.mock.OpenAPIExpectation.openAPIExpectation;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
public class OpenAPIExpectationSerializerIntegrationTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void shouldIgnoreExtraFields() {
        // given
        String requestBytes = "" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"," + NEW_LINE +
            "  \"extra_field\": \"extra_value\"," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";

        // then
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("incorrect openapi expectation json format for:" + NEW_LINE +
            "" + NEW_LINE +
            "  {" + NEW_LINE +
            "    \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"," + NEW_LINE +
            "    \"extra_field\": \"extra_value\"," + NEW_LINE +
            "    \"operationsAndResponses\" : {" + NEW_LINE +
            "      \"listPets\" : \"200\"," + NEW_LINE +
            "      \"createPets\" : \"201\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "" + NEW_LINE +
            " schema validation errors:" + NEW_LINE +
            "" + NEW_LINE +
            "  1 error:" + NEW_LINE +
            "   - object instance has properties which are not allowed by the schema: [\"extra_field\"]" + NEW_LINE +
            "  " + NEW_LINE +
            "  " + OPEN_API_SPECIFICATION_URL);

        // when
        new OpenAPIExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);
    }

    @Test
    public void shouldDeserializeCompleteObject() {
        // given
        String requestBytes = "" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";

        // when
        OpenAPIExpectation httpResponse = new OpenAPIExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(openAPIExpectation()
            .withSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
            .withOperationsAndResponses(ImmutableMap.of(
                "listPets", "200",
                "createPets", "201"
            )), httpResponse);
    }

    @Test
    public void shouldDeserializePartialObject() {
        // given
        String requestBytes = "" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"" + NEW_LINE +
            "}";

        // when
        OpenAPIExpectation httpResponse = new OpenAPIExpectationSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new OpenAPIExpectationDTO()
            .setSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
            .buildObject(), httpResponse);
    }

    @Test
    public void shouldSerializeCompleteObject() {
        // when
        String jsonOpenAPIExpectation = new OpenAPIExpectationSerializer(new MockServerLogger()).serialize(
            openAPIExpectation()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
                .withOperationsAndResponses(ImmutableMap.of(
                    "listPets", "200",
                    "createPets", "201"
                ))
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", jsonOpenAPIExpectation);
    }

    @Test
    @SuppressWarnings("RedundantArrayCreation")
    public void shouldSerializeArray() {
        // when
        String jsonOpenAPIExpectation = new OpenAPIExpectationSerializer(new MockServerLogger()).serialize(
            new OpenAPIExpectation[]{
                openAPIExpectation()
                    .withSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
                    .withOperationsAndResponses(ImmutableMap.of(
                    "listPets", "200",
                    "createPets", "201"
                )),
                openAPIExpectation()
                    .withSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
                    .withOperationsAndResponses(ImmutableMap.of(
                    "listPets", "200",
                    "createPets", "201"
                ))
            }
        );

        // then
        assertEquals("[ {" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}, {" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "} ]", jsonOpenAPIExpectation);
    }

    @Test
    public void shouldSerializeList() {
        // when
        String jsonOpenAPIExpectation = new OpenAPIExpectationSerializer(new MockServerLogger()).serialize(
            Arrays.asList(
                openAPIExpectation()
                    .withSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
                    .withOperationsAndResponses(ImmutableMap.of(
                        "listPets", "200",
                        "createPets", "201"
                    )),
                openAPIExpectation()
                    .withSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
                    .withOperationsAndResponses(ImmutableMap.of(
                        "listPets", "200",
                        "createPets", "201"
                    ))
            )
        );

        // then
        assertEquals("[ {" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}, {" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "} ]", jsonOpenAPIExpectation);
    }

    @Test
    public void shouldReturnJsonWithNoFieldsSet() {
        assertThat(new OpenAPIExpectationSerializer(new MockServerLogger()).serialize(openAPIExpectation()), is("{ }"));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIClasspathLocationAndOperationId() {
        assertThat(new OpenAPIExpectationSerializer(new MockServerLogger()).serialize(
            openAPIExpectation()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
                .withOperationsAndResponses(ImmutableMap.of(
                    "listPets", "200",
                    "createPets", "201"
                ))
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIClasspathLocation() {
        assertThat(new OpenAPIExpectationSerializer(new MockServerLogger()).serialize(
            openAPIExpectation()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIUrlAndOperationId() {
        assertThat(new OpenAPIExpectationSerializer(new MockServerLogger()).serialize(
            openAPIExpectation()
                .withSpecUrlOrPayload(FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString())
                .withOperationsAndResponses(ImmutableMap.of(
                    "listPets", "200",
                    "createPets", "201"
                ))
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"" + FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString() + "\"," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIUrl() {
        assertThat(new OpenAPIExpectationSerializer(new MockServerLogger()).serialize(
            openAPIExpectation()
                .withSpecUrlOrPayload(FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString())
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"" + FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString() + "\"" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPISpecAndOperationId() throws JsonProcessingException {
        assertThat(new OpenAPIExpectationSerializer(new MockServerLogger()).serialize(
            openAPIExpectation()
                .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json"))
                .withOperationsAndResponses(ImmutableMap.of(
                    "listPets", "200",
                    "createPets", "201"
                ))
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : " + ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json")).toPrettyString().replaceAll("\\R", "\n  ") + "," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPISpec() throws JsonProcessingException {
        assertThat(new OpenAPIExpectationSerializer(new MockServerLogger()).serialize(
            openAPIExpectation()
                .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json"))
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : " + ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json")).toPrettyString().replaceAll("\\R", "\n  ") + NEW_LINE +
            "}"
        ));
    }
}
