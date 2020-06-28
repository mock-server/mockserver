package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.mockserver.model.JsonSchemaBody;
import org.mockserver.model.ParameterStyle;
import org.mockserver.serialization.ObjectMapperFactory;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Not.not;

public class JsonSchemaBodySerializerTest {

    @Test
    public void shouldSerializeJsonBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonSchemaBody("{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"integer\"}}, \"required\": [\"id\"]}")),
            is("{\"type\":\"JSON_SCHEMA\",\"jsonSchema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}},\"required\":[\"id\"]}}"));
    }

    @Test
    public void shouldSerializeJsonBodyDTOWithParameterStyle() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(
            new JsonSchemaBody("{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"integer\"}}, \"required\": [\"id\"]}")
                .withParameterStyles(ImmutableMap.of("pipeDelimitedParameter", ParameterStyle.PIPE_DELIMITED))
            ),
            is("{\"type\":\"JSON_SCHEMA\",\"jsonSchema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}},\"required\":[\"id\"]},\"parameterStyles\":{\"pipeDelimitedParameter\":\"PIPE_DELIMITED\"}}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new JsonSchemaBody("{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"integer\"}}, \"required\": [\"id\"]}"))),
            is("{\"not\":true,\"type\":\"JSON_SCHEMA\",\"jsonSchema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}},\"required\":[\"id\"]}}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithOptional() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonSchemaBody("{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"integer\"}}, \"required\": [\"id\"]}").withOptional(true)),
            is("{\"optional\":true,\"type\":\"JSON_SCHEMA\",\"jsonSchema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}},\"required\":[\"id\"]}}"));
    }

}