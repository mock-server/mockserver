package org.mockserver.client.serialization.serializers.string;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.NottableString;
import org.mockserver.model.StringBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Not.not;
import static org.mockserver.model.NottableString.string;

public class NottableStringSerializerTest {

    @Test
    public void shouldSerializeObjectWithNottableString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new Object() {
                    public NottableString getValue() {
                        return string("some_string");
                    }
                }),
                is("{\"value\":\"some_string\"}"));
    }

    @Test
    public void shouldSerializeObjectWithNottedNottableString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new Object() {
                    public NottableString getValue() {
                        return NottableString.not("some_string");
                    }
                }),
                is("{\"value\":\"!some_string\"}"));
    }

    @Test
    public void shouldSerializeNottableString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(string("some_string")),
                is("\"some_string\""));
    }

    @Test
    public void shouldSerializeNotNottableString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(org.mockserver.model.NottableString.not("some_string")),
                is("\"!some_string\""));
    }

    @Test
    public void shouldSerializeNotNottableStringWithExclamationMark() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(org.mockserver.model.NottableString.not("!some_string")),
                is("{\"not\":true,\"value\":\"!some_string\"}"));
    }

    @Test
    public void shouldSerializeNottableStringWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(string("some_string"))),
                is("\"!some_string\""));
    }

    @Test
    public void shouldSerializeNottableStringWithExclamationMarkAndNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(string("!some_string"))),
                is("{\"not\":true,\"value\":\"!some_string\"}"));
    }
}