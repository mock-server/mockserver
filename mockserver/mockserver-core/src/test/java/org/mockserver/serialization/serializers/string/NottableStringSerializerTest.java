package org.mockserver.serialization.serializers.string;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.model.NottableOptionalString;
import org.mockserver.model.NottableString;
import org.mockserver.serialization.ObjectMapperFactory;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.NottableOptionalString.optional;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.ParameterStyle.MATRIX;

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
    public void shouldSerializeObjectWithStyledString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new Object() {
                public NottableString getValue() {
                    return string("some_string").withStyle(MATRIX);
                }
            }),
            is("{\"value\":{\"parameterStyle\":\"MATRIX\",\"value\":\"some_string\"}}"));
    }

    @Test
    public void shouldSerializeObjectWithNottedString() throws JsonProcessingException {
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
                    return optional("some_string");
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
            is("{\"value\":{\"schema\":{\"type\":\"string\"}}}"));
    }

    @Test
    public void shouldSerializeObjectWithNottedSchemaString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new Object() {
                public NottableString getValue() {
                    return schemaString("{\"type\":\"string\"}", true);
                }
            }),
            is("{\"value\":{\"not\":true,\"schema\":{\"type\":\"string\"}}}"));
    }

    @Test
    public void shouldSerializeNottableString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(string("some_string")),
            is("\"some_string\""));
    }

    @Test
    public void shouldSerializeNottedStringWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(string("some_string", true)),
            is("\"!some_string\""));
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(string("!some_string")),
            is("\"!some_string\""));
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(org.mockserver.model.NottableString.not("some_string")),
            is("\"!some_string\""));
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(string("!some_string", true)),
            is("\"!!some_string\""));
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(org.mockserver.model.NottableString.not("!some_string")),
            is("\"!!some_string\""));
    }

    @Test
    public void shouldSerializeOptionalString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(optional("some_string")),
            is("\"?some_string\""));
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(string("?some_string")),
            is("\"?some_string\""));
    }

    @Test
    public void shouldSerializeOptionalNottedString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(NottableOptionalString.optional("some_string", true)),
            is("\"?!some_string\""));
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(string("?!some_string")),
            is("\"?!some_string\""));
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(string("!?some_string")),
            is("\"?!some_string\""));
    }

    @Test
    public void shouldSerializeSchemaString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(schemaString("" +
                "{" + NEW_LINE +
                "  \"type\" : \"string\"" + NEW_LINE +
                "}")),
            is("{\"schema\":{\"type\":\"string\"}}"));
    }

    @Test
    public void shouldSerializeNottedSchemaString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(schemaString("{\"type\":\"string\"}", true)),
            is("{\"not\":true,\"schema\":{\"type\":\"string\"}}"));
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(schemaString("!{\"type\":\"string\"}")),
            is("{\"not\":true,\"schema\":{\"type\":\"string\"}}"));
    }

    @Test
    public void shouldSerializeStyledString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper()
                .writeValueAsString(
                    string("some_string")
                        .withStyle(MATRIX)
                ),
            is("{\"parameterStyle\":\"MATRIX\",\"value\":\"some_string\"}"));
    }

    @Test
    public void shouldSerializeStyledSchemaString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper()
                .writeValueAsString(
                    schemaString("" +
                        "{" + NEW_LINE +
                        "  \"type\" : \"string\"" + NEW_LINE +
                        "}")
                        .withStyle(MATRIX)
                ),
            is("{\"parameterStyle\":\"MATRIX\",\"schema\":{\"type\":\"string\"}}"));
    }

    @Test
    public void shouldSerializeNottedStyledString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper()
                .writeValueAsString(
                    string("some_string", true)
                        .withStyle(MATRIX)
                ),
            is("{\"not\":true,\"parameterStyle\":\"MATRIX\",\"value\":\"some_string\"}"));
    }

    @Test
    public void shouldSerializeOptionalNottedStyledString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper()
                .writeValueAsString(
                    NottableOptionalString.optional("some_string", true)
                        .withStyle(MATRIX)
                ),
            is("{\"not\":true,\"optional\":true,\"parameterStyle\":\"MATRIX\",\"value\":\"some_string\"}"));
        assertThat(ObjectMapperFactory.createObjectMapper()
                .writeValueAsString(
                    string("?!some_string")
                        .withStyle(MATRIX)
                ),
            is("{\"not\":true,\"optional\":true,\"parameterStyle\":\"MATRIX\",\"value\":\"some_string\"}"));
    }
}
