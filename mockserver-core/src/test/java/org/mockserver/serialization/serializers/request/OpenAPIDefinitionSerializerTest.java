package org.mockserver.serialization.serializers.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Test;
import org.mockserver.file.FileReader;
import org.mockserver.serialization.ObjectMapperFactory;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.OpenAPIDefinition.openAPI;

public class OpenAPIDefinitionSerializerTest {

    private final ObjectWriter objectMapper = ObjectMapperFactory.createObjectMapper(true);

    @Test
    public void shouldReturnJsonWithNoFieldsSet() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(openAPI()), is("{ }"));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIClasspathLocationAndOperationId() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(
            openAPI()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
                .withOperationId("listPets")
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"," + NEW_LINE +
            "  \"operationId\" : \"listPets\"" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIClasspathLocation() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(
            openAPI()
                .withSpecUrlOrPayload("org/mockserver/mock/openapi_simple_example.json")
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/mock/openapi_simple_example.json\"" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIUrlAndOperationId() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(
            openAPI()
                .withSpecUrlOrPayload(FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString())
                .withOperationId("listPets")
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"" + FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString() + "\"," + NEW_LINE +
            "  \"operationId\" : \"listPets\"" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIUrl() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(
            openAPI()
                .withSpecUrlOrPayload(FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString())
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"" + FileReader.getURL("org/mockserver/mock/openapi_simple_example.json").toString() + "\"" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPISpecAndOperationId() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(
            openAPI()
                .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json"))
                .withOperationId("listPets")
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : " + ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json")).toPrettyString().replaceAll("\\R", "\n  ") + "," + NEW_LINE +
            "  \"operationId\" : \"listPets\"" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPISpec() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(
            openAPI()
                .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json"))
        ), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : " + ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/mock/openapi_simple_example.json")).toPrettyString().replaceAll("\\R", "\n  ") + "" + NEW_LINE +
            "}"
        ));
    }

}