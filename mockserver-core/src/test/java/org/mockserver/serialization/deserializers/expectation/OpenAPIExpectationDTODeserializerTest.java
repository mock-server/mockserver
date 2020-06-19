package org.mockserver.serialization.deserializers.expectation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.mockserver.file.FileReader;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.OpenAPIExpectationDTO;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.mock.OpenAPIExpectation.openAPIExpectation;

public class OpenAPIExpectationDTODeserializerTest {

    @Test
    public void shouldParseJsonWithNoFieldsSetAndDefaultToHttpRequest() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{ }", OpenAPIExpectationDTO.class), is(new OpenAPIExpectationDTO()));
    }

    @Test
    public void shouldParseJsonWithOpenAPIClasspathLocationAndOperationId() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", OpenAPIExpectationDTO.class), is(
            new OpenAPIExpectationDTO(openAPIExpectation()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
                .withOperationsAndResponses(ImmutableMap.of(
                    "listPets", "200",
                    "createPets", "201"
                ))
            )));
    }

    @Test
    public void shouldParseJsonWithOpenAPIClasspathLocation() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"" + NEW_LINE +
            "}", OpenAPIExpectationDTO.class), is(
            new OpenAPIExpectationDTO(openAPIExpectation()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
            )));
    }

    @Test
    public void shouldParseJsonWithOpenAPIUrlAndOperationId() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"" + FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString() + "\"," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", OpenAPIExpectationDTO.class), is(
            new OpenAPIExpectationDTO(openAPIExpectation()
                .withSpecUrlOrPayload(FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString())
                .withOperationsAndResponses(ImmutableMap.of(
                    "listPets", "200",
                    "createPets", "201"
                ))
            )));
    }

    @Test
    public void shouldParseJsonWithOpenAPIUrl() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"" + FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString() + "\"" + NEW_LINE +
            "}", OpenAPIExpectationDTO.class), is(
            new OpenAPIExpectationDTO(openAPIExpectation()
                .withSpecUrlOrPayload(FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString())
            )));
    }

    @Test
    public void shouldParseJsonWithOpenAPISpecAndOperationId() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : " + ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json")).toPrettyString().replaceAll("\\R", "\n  ") + "," + NEW_LINE +
            "  \"operationsAndResponses\" : {" + NEW_LINE +
            "    \"listPets\" : \"200\"," + NEW_LINE +
            "    \"createPets\" : \"201\"" + NEW_LINE +
            "  }" + NEW_LINE +
            "}", OpenAPIExpectationDTO.class), is(
            new OpenAPIExpectationDTO(openAPIExpectation()
                .withSpecUrlOrPayload(ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json")).toPrettyString())
                .withOperationsAndResponses(ImmutableMap.of(
                    "listPets", "200",
                    "createPets", "201"
                ))
            )));
    }

    @Test
    public void shouldParseJsonWithOpenAPISpec() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : " + ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json")).toPrettyString().replaceAll("\\R", "\n  ") + NEW_LINE +
            "}", OpenAPIExpectationDTO.class), is(
            new OpenAPIExpectationDTO(openAPIExpectation()
                .withSpecUrlOrPayload(ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json")).toPrettyString())
            )));
    }

}