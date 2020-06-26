package org.mockserver.serialization.deserializers.string;

import org.junit.Test;
import org.mockserver.model.NottableString;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.ExpectationDTO;
import org.mockserver.serialization.model.HttpRequestDTO;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.NottableOptionalString.optionalString;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class NottableStringDeserializerTest {

    @Test
    public void shouldDeserializeNottableString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("\"some_string\"", NottableString.class),
            is(string("some_string")));
    }

    @Test
    public void shouldDeserializeNotNottableString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("\"!some_string\"", NottableString.class),
            is(NottableString.not("some_string")));
    }

    @Test
    public void shouldDeserializeOptionalString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("\"?some_string\"", NottableString.class),
            is(optionalString("some_string")));
    }

    @Test
    public void shouldDeserializeOptionalNottedString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("\"?!some_string\"", NottableString.class),
            is(optionalString("some_string", true)));
    }

    @Test
    public void shouldDeserializeSchemaString() throws IOException {
        NottableString actual = ObjectMapperFactory.createObjectMapper().readValue("{\"type\":\"string\"}", NottableString.class);
        assertThat(actual,
            is(schemaString("{" + NEW_LINE +
                "  \"type\" : \"string\"" + NEW_LINE +
                "}")));
    }

    @Test
    public void shouldDeserializeNottedSchemaString() throws IOException {
        NottableString actual = ObjectMapperFactory.createObjectMapper().readValue("{\"type\":\"string\",\"not\":true}", NottableString.class);
        assertThat(actual,
            is(schemaString("{" + NEW_LINE +
                "  \"type\" : \"string\"" + NEW_LINE +
                "}", true)));
    }

    @Test
    public void shouldParseJSONWithMethod() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"method\" : \"HEAD\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("HEAD"))
            )));
    }

    @Test
    public void shouldParseJSONWithMethodWithNot() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"method\": \"!HEAD\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(NottableString.not("HEAD"))
            )));
    }

    @Test
    public void shouldParseJSONWithOptionalMethod() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"method\": \"?HEAD\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(optionalString("HEAD"))
            )));
    }

    @Test
    public void shouldParseJSONWithSchemaMethod() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"method\": { \"type\": \"string\" }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(schemaString("{" + NEW_LINE +
                        "  \"type\" : \"string\"" + NEW_LINE +
                        "}"))
            )));
    }

    @Test
    public void shouldParseJSONWithNottedSchemaMethod() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"method\": {\"type\":\"string\",\"not\":true}" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(schemaString("{" + NEW_LINE +
                        "  \"type\" : \"string\"" + NEW_LINE +
                        "}", true))
            )));
    }

    @Test
    public void shouldParseJSONWithPath() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"path\" : \"/some/path\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(string("/some/path"))
            )));
    }

    @Test
    public void shouldParseJSONWithPathWithNot() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"path\": \"!/some/path\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(NottableString.not("/some/path"))
            )));
    }

    @Test
    public void shouldParseJSONWithOptionalPath() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"path\": \"?/some/path\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(optionalString("/some/path"))
            )));
    }

    @Test
    public void shouldParseJSONWithSchemaPath() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"path\": { \"type\": \"string\" }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(schemaString("{" + NEW_LINE +
                        "  \"type\" : \"string\"" + NEW_LINE +
                        "}"))
            )));
    }

    @Test
    public void shouldParseJSONWithNottedSchemaPath() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"path\": {\"type\":\"string\",\"not\":true}" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpRequest(
                new HttpRequestDTO()
                    .setPath(schemaString("{" + NEW_LINE +
                        "  \"type\" : \"string\"," + NEW_LINE +
                        "  \"not\" : true" + NEW_LINE +
                        "}"))
            )));
    }

}
