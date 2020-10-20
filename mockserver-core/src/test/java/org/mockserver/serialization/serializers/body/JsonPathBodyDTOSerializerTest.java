package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.model.JsonPathBody;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.JsonPathBodyDTO;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class JsonPathBodyDTOSerializerTest {

    @Test
    public void shouldSerializeJsonPathBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonPathBodyDTO(new JsonPathBody("\\some\\path"))),
                is("{\"type\":\"JSON_PATH\",\"jsonPath\":\"\\\\some\\\\path\"}"));
    }

    @Test
    public void shouldSerializeJsonPathBodyDTOWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonPathBodyDTO(new JsonPathBody("\\some\\path"), true)),
                is("{\"not\":true,\"type\":\"JSON_PATH\",\"jsonPath\":\"\\\\some\\\\path\"}"));
    }

    @Test
    public void shouldSerializeJsonPathBodyDTOWithOptional() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonPathBodyDTO(new JsonPathBody("\\some\\path")).withOptional(true)),
                is("{\"optional\":true,\"type\":\"JSON_PATH\",\"jsonPath\":\"\\\\some\\\\path\"}"));
    }

}