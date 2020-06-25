package org.mockserver.serialization.serializers.string;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.model.NottableString;
import org.mockserver.serialization.ObjectMapperFactory;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.NottableOptionalString.optionalString;
import static org.mockserver.model.NottableSchemaString.schemaString;
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
    public void shouldSerializeObjectWithOptionalString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new Object() {
                public NottableString getValue() {
                    return optionalString("some_string");
                }
            }),
            is("{\"value\":\"?some_string\"}"));
    }

    @Test
    public void shouldSerializeObjectWithSchemaString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new Object() {
                public NottableString getValue() {
                    return schemaString("{\"type\":\"string\"}");
                }
            }),
            is("{\"value\":{\"type\":\"string\"}}"));
    }

    @Test
    public void shouldSerializeObjectWithNottedSchemaString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new Object() {
                public NottableString getValue() {
                    return schemaString("{\"type\":\"string\"}", true);
                }
            }),
            is("{\"value\":{\"type\":\"string\",\"not\":true}}"));
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
            is("\"!!some_string\""));
    }

    @Test
    public void shouldSerializeNottableStringWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(string("some_string", true)),
            is("\"!some_string\""));
    }

    @Test
    public void shouldSerializeNottableStringWithExclamationMarkAndNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(string("!some_string", true)),
            is("\"!!some_string\""));
    }

    @Test
    public void shouldSerializeOptionalString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(optionalString("some_string")),
            is("\"?some_string\""));
    }

    @Test
    public void shouldSerializeOptionalNottedString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(optionalString("some_string", true)),
            is("\"?!some_string\""));
    }

    @Test
    public void shouldSerializeSchemaString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(schemaString("" +
                "{" + NEW_LINE +
                "  \"type\" : \"string\"" + NEW_LINE +
                "}")),
            is("{\"type\":\"string\"}"));
    }

    @Test
    public void shouldSerializeNottedSchemaString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(schemaString("{\"type\":\"string\"}", true)),
            is("{\"type\":\"string\",\"not\":true}"));
    }
}
