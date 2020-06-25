package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.model.JsonPathBody;
import org.mockserver.serialization.ObjectMapperFactory;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Not.not;

public class JsonPathBodySerializerTest {

    @Test
    public void shouldSerializeJsonPathBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonPathBody("\\some\\path")),
                is("{\"type\":\"JSON_PATH\",\"jsonPath\":\"\\\\some\\\\path\"}"));
    }

    @Test
    public void shouldSerializeJsonPathBodyWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new JsonPathBody("\\some\\path"))),
                is("{\"not\":true,\"type\":\"JSON_PATH\",\"jsonPath\":\"\\\\some\\\\path\"}"));
    }

    @Test
    public void shouldSerializeJsonPathBodyWithOptional() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonPathBody("\\some\\path").withOptional(true)),
                is("{\"optional\":true,\"type\":\"JSON_PATH\",\"jsonPath\":\"\\\\some\\\\path\"}"));
    }

}