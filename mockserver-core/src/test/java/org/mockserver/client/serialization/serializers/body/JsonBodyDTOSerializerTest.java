package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.JsonBodyDTO;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.JsonBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class JsonBodyDTOSerializerTest {

    @Test
    public void shouldSerializeJsonBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonBodyDTO(new JsonBody("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}"))),
                is("{\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithDefaultMatchType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonBodyDTO(new JsonBody("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MatchType.ONLY_MATCHING_FIELDS))),
                is("{\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyDTOWithMatchType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonBodyDTO(new JsonBody("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MatchType.STRICT))),
                is("{\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\",\"matchType\":\"STRICT\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyDTOWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonBodyDTO(new JsonBody("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MatchType.STRICT), true)),
                is("{\"not\":true,\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\",\"matchType\":\"STRICT\"}"));
    }
}