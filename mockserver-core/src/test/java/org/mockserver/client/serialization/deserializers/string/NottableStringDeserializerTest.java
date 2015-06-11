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
import static org.junit.Assert.assertThat;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class NottableStringDeserializerTest {

    @Test
    public void shouldSerializeNottableString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("\"some_string\"", NottableString.class),
                is(string("some_string")));

        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":false,\"value\":\"some_string\"}", NottableString.class),
                is(string("some_string")));
    }

    @Test
    public void shouldSerializeNotNottableString() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("\"!some_string\"", NottableString.class),
                is(not("some_string")));

        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":true,\"value\":\"some_string\"}", NottableString.class),
                is(not("some_string")));
    }

    @Test
    public void shouldSerializeNottableStringWithExclamationMark() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":false,\"value\":\"!some_string\"}", NottableString.class),
                is(string("!some_string")));
    }


    @Test
    public void shouldSerializeNottableStringWithNot() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"not\":true,\"value\":\"some_string\"}", NottableString.class),
                is(Not.not(string("some_string"))));
    }

    @Test
    public void shouldParseJSONWithMethodWithNot() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"method\" : {" + System.getProperty("line.separator") +
                "            \"not\" : true," + System.getProperty("line.separator") +
                "            \"value\" : \"HEAD\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setMethod(not("HEAD"))
                )));
    }

    @Test
    public void shouldParseJSONWithMethod() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"method\" : \"HEAD\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
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
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"path\" : {" + System.getProperty("line.separator") +
                "            \"not\" : true," + System.getProperty("line.separator") +
                "            \"value\" : \"/some/path\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setPath(not("/some/path"))
                )));
    }

    @Test
    public void shouldParseJSONWithPath() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"path\" : \"/some/path\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
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
