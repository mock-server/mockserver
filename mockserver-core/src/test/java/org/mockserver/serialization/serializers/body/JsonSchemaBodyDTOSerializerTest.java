package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.JsonSchemaBodyDTO;
import org.mockserver.model.JsonSchemaBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class JsonSchemaBodyDTOSerializerTest {

    @Test
    public void shouldSerializeJsonBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonSchemaBodyDTO(new JsonSchemaBody("{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"integer\"}}, \"required\": [\"id\"]}"), false)),
                is("{\"type\":\"JSON_SCHEMA\",\"jsonSchema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}},\"required\":[\"id\"]}}"));
    }

    @Test
    public void shouldSerializeJsonBodyDTOWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonSchemaBodyDTO(new JsonSchemaBody("{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"integer\"}}, \"required\": [\"id\"]}"), true)),
                is("{\"not\":true,\"type\":\"JSON_SCHEMA\",\"jsonSchema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}},\"required\":[\"id\"]}}"));
    }

}