package org.mockserver.serialization.serializers.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Test;
import org.mockserver.file.FilePath;
import org.mockserver.file.FileReader;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.OpenAPIDefinitionDTO;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.OpenAPIDefinition.openAPI;

public class OpenAPIDefinitionDTOSerializerTest {

    private final ObjectWriter objectMapper = ObjectMapperFactory.createObjectMapper(true, false);

    @Test
    public void shouldReturnJsonWithNoFieldsSet() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(new OpenAPIDefinitionDTO(openAPI())), is("{ }"));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIClasspathLocationAndOperationId() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(new OpenAPIDefinitionDTO(
            openAPI()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_simple_example.json")
                .withOperationId("listPets")
        )), is("" +
            "{" + NEW_LINE +
            "  \"operationId\" : \"listPets\"," + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/openapi/openapi_simple_example.json\"" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIClasspathLocation() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(new OpenAPIDefinitionDTO(
            openAPI()
                .withSpecUrlOrPayload("org/mockserver/openapi/openapi_simple_example.json")
        )), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"org/mockserver/openapi/openapi_simple_example.json\"" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIUrlAndOperationId() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(new OpenAPIDefinitionDTO(
            openAPI()
                .withSpecUrlOrPayload(FilePath.getURL("org/mockserver/openapi/openapi_simple_example.json").toString())
                .withOperationId("listPets")
        )), is("" +
            "{" + NEW_LINE +
            "  \"operationId\" : \"listPets\"," + NEW_LINE +
            "  \"specUrlOrPayload\" : \"" + FilePath.getURL("org/mockserver/openapi/openapi_simple_example.json").toString() + "\"" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPIUrl() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(new OpenAPIDefinitionDTO(
            openAPI()
                .withSpecUrlOrPayload(FilePath.getURL("org/mockserver/openapi/openapi_simple_example.json").toString())
        )), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : \"" + FilePath.getURL("org/mockserver/openapi/openapi_simple_example.json").toString() + "\"" + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPISpecAndOperationId() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(new OpenAPIDefinitionDTO(
            openAPI()
                .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_simple_example.json"))
                .withOperationId("listPets")
        )), is("" +
            "{" + NEW_LINE +
            "  \"operationId\" : \"listPets\"," + NEW_LINE +
            "  \"specUrlOrPayload\" : " + ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_simple_example.json")).toPrettyString().replaceAll("\\R", "\n  ") + NEW_LINE +
            "}"
        ));
    }

    @Test
    public void shouldReturnJsonWithOpenAPISpec() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(new OpenAPIDefinitionDTO(
            openAPI()
                .withSpecUrlOrPayload(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_simple_example.json"))
        )), is("" +
            "{" + NEW_LINE +
            "  \"specUrlOrPayload\" : " + ObjectMapperFactory.createObjectMapper().readTree(FileReader.readFileFromClassPathOrPath("org/mockserver/openapi/openapi_simple_example.json")).toPrettyString().replaceAll("\\R", "\n  ") + NEW_LINE +
            "}"
        ));
    }

}