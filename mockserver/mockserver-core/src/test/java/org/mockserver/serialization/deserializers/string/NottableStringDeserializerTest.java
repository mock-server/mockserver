package org.mockserver.serialization.deserializers.string;

import org.junit.Test;
import org.mockserver.model.NottableOptionalString;
import org.mockserver.model.NottableString;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.ExpectationDTO;
import org.mockserver.serialization.model.HttpRequestDTO;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.NottableOptionalString.optional;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.ParameterStyle.MATRIX;

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
    public void shouldDeserializeNottedString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("\"!some_string\"", NottableString.class),
            is(NottableString.not("some_string")));
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":true,\"value\":\"some_string\"}", NottableString.class),
            is(NottableString.not("some_string")));
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":true,\"optional\":false,\"value\":\"some_string\"}", NottableString.class),
            is(NottableString.not("some_string")));
    }

    @Test
    public void shouldDeserializeOptionalString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("\"?some_string\"", NottableString.class),
            is(optional("some_string")));
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"optional\":true,\"value\":\"some_string\"}", NottableString.class),
            is(optional("some_string")));
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":false,\"optional\":true,\"value\":\"some_string\"}", NottableString.class),
            is(optional("some_string")));
    }

    @Test
    public void shouldDeserializeNottedOptionalString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("\"?!some_string\"", NottableString.class),
            is(NottableOptionalString.optional("some_string", true)));
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("\"!?some_string\"", NottableString.class),
            is(NottableOptionalString.optional("some_string", true)));
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":true,\"optional\":true,\"value\":\"some_string\"}", NottableString.class),
            is(string("?!some_string")));
    }

    @Test
    public void shouldDeserializeStyledOptionalString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"optional\":true,\"parameterStyle\":\"MATRIX\",\"value\":\"some_string\"}", NottableString.class),
            is(string("?some_string").withStyle(MATRIX)));
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":false,\"optional\":true,\"parameterStyle\":\"MATRIX\",\"value\":\"some_string\"}", NottableString.class),
            is(string("?some_string").withStyle(MATRIX)));
    }

    @Test
    public void shouldDeserializeStyledNottedOptionalString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":true,\"optional\":true,\"parameterStyle\":\"MATRIX\",\"value\":\"some_string\"}", NottableString.class),
            is(string("?!some_string").withStyle(MATRIX)));
    }

    @Test
    public void shouldDeserializeSchemaString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"schema\":{\"type\":\"string\"}}", NottableString.class),
            is(schemaString("{" + NEW_LINE +
                "  \"type\" : \"string\"" + NEW_LINE +
                "}")));
    }

    @Test
    public void shouldDeserializeNottedSchemaString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":true,\"schema\":{\"type\":\"string\"}}", NottableString.class),
            is(schemaString("{" + NEW_LINE +
                "  \"type\" : \"string\"" + NEW_LINE +
                "}", true)));
    }

    @Test
    public void shouldDeserializeStyledSchemaString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":true,\"parameterStyle\":\"MATRIX\",\"schema\":{\"type\":\"string\"}}", NottableString.class),
            is(schemaString("{" + NEW_LINE +
                "  \"type\" : \"string\"" + NEW_LINE +
                "}", true).withStyle(MATRIX)));
    }

    @Test
    public void shouldDeserializeNottedStyledSchemaString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":true,\"parameterStyle\":\"MATRIX\",\"schema\":{\"type\":\"string\"}}", NottableString.class),
            is(schemaString("{" + NEW_LINE +
                "  \"type\" : \"string\"" + NEW_LINE +
                "}", true).withStyle(MATRIX)));
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
    public void shouldParseJSONWithMethodWithEmptyString() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"method\" : \"\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpRequest(
                new HttpRequestDTO()
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
                    .setMethod(optional("HEAD"))
            )));
    }

    @Test
    public void shouldParseJSONWithSchemaMethod() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"method\": {\"schema\":{\"type\":\"string\"}}" + NEW_LINE +
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
            "        \"method\": {\"not\":true,\"schema\":{\"type\":\"string\"}}" + NEW_LINE +
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
    public void shouldParseJSONWithStyledMethod() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"method\": {\"parameterStyle\":\"MATRIX\",\"value\":\"HEAD\"}" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpRequest(
                new HttpRequestDTO()
                    .setMethod(string("HEAD").withStyle(MATRIX))
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
                    .setPath(optional("/some/path"))
            )));
    }

    @Test
    public void shouldParseJSONWithSchemaPath() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"path\": {\"schema\":{\"type\":\"string\"}}" + NEW_LINE +
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
            "        \"path\": {\"not\":true,\"schema\":{\"type\":\"string\"}}" + NEW_LINE +
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
