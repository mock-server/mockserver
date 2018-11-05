package org.mockserver.client.serialization.deserializers.string;

import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.ExpectationDTO;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.model.Not;
import org.mockserver.model.NottableString;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Not.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class NottableStringDeserializerTest {

    @Test
    public void shouldDeserializeNottableString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("\"some_string\"", NottableString.class),
            is(string("some_string")));

        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":false,\"value\":\"some_string\"}", NottableString.class),
            is(string("some_string")));
    }

    @Test
    public void shouldDeserializeNotNottableString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("\"!some_string\"", NottableString.class),
            is(NottableString.not("some_string")));

        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":true,\"value\":\"some_string\"}", NottableString.class),
            is(NottableString.not("some_string")));
    }

    @Test
    public void shouldDeserializeNottableStringWithExclamationMark() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":false,\"value\":\"!some_string\"}", NottableString.class),
            is(string("!some_string")));
    }


    @Test
    public void shouldDeserializeNottableStringWithNot() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":true,\"value\":\"some_string\"}", NottableString.class),
            is(NottableString.not("some_string")));
    }

    @Test
    public void shouldParseJSONWithMethodWithNot() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"method\" : {" + NEW_LINE +
            "            \"not\" : true," + NEW_LINE +
            "            \"value\" : \"HEAD\"" + NEW_LINE +
            "        }" + NEW_LINE +
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
    public void shouldParseJSONWithPathWithNot() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpRequest\": {" + NEW_LINE +
            "        \"path\" : {" + NEW_LINE +
            "            \"not\" : true," + NEW_LINE +
            "            \"value\" : \"/some/path\"" + NEW_LINE +
            "        }" + NEW_LINE +
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


    /*
          "name" : {
	        "not" : false,
	        "value" : "!name"
	      },
	      "value" : {
	        "not" : true,
	        "value" : "!value"
	      }
     */
}
