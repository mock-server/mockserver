package org.mockserver.serialization.serializers.expectation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.mockserver.file.FilePath;
import org.mockserver.file.FileReader;
import org.mockserver.serialization.ObjectMapperFactory;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.mock.OpenAPIExpectation.openAPIExpectation;
import static org.mockserver.model.OpenAPIDefinition.openAPI;

public class OpenAPIExpectationSerializerTest {

    private final ObjectWriter objectMapper = ObjectMapperFactory.createObjectMapper(true, false);

    @Test
    public void shouldReturnJsonWithNoFieldsSet() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(openAPIExpectation()), is("{ }"));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIClasspathLocationAndOperationId() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(
            openAPIExpectation()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_simple_example.json")
                .withOperationsAndResponses(ImmutableMap.of(
                    "listPets", "200",
                    "createPets", "201"
                ))
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/openapi/openapi_simple_example.json\"," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIClasspathLocation() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(
            openAPIExpectation()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_simple_example.json")
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/openapi/openapi_simple_example.json\"" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIUrlAndOperationId() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(
            openAPIExpectation()
                .withSpecUrlOrPayload(FilePath.getURL("org/mockserver/openapi/openapi_simple_example.json").toString())
                .withOperationsAndResponses(ImmutableMap.of(
                    "listPets", "200",
                    "createPets", "201"
                ))
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"" + FilePath.getURL("org/mockserver/openapi/openapi_simple_example.json").toString() + "\"," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIUrl() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(
            openAPIExpectation()
                .withSpecUrlOrPayload(FilePath.getURL("org/mockserver/openapi/openapi_simple_example.json").toString())
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"" + FilePath.getURL("org/mockserver/openapi/openapi_simple_example.json").toString() + "\"" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPISpecAndOperationId() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(
            openAPIExpectation()
                .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_simple_example.json"))
                .withOperationsAndResponses(ImmutableMap.of(
                    "listPets", "200",
                    "createPets", "201"
                ))
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : " + ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_simple_example.json")).toPrettyString().replaceAll("\\R", "\n  ") + "," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPISpec() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(
            openAPIExpectation()
                .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_simple_example.json"))
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : " + ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_simple_example.json")).toPrettyString().replaceAll("\\R", "\n  ") + NEW_LINE +
            "}"
        ));
    }

}