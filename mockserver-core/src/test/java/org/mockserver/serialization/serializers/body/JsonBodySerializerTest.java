package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.MediaType;
import org.junit.Test;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.matchers.MatchType;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.Not.not;

public class JsonBodySerializerTest {

    public class TestObject {
        private String fieldOne = "valueOne";
        private String fieldTwo = "valueTwo";

        public String getFieldOne() {
            return fieldOne;
        }

        public void setFieldOne(String fieldOne) {
            this.fieldOne = fieldOne;
        }

        public String getFieldTwo() {
            return fieldTwo;
        }

        public void setFieldTwo(String fieldTwo) {
            this.fieldTwo = fieldTwo;
        }
    }

    @Test
    public void shouldSerializeJsonBodyAsObject() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(json(new TestObject())),
                is("{\"type\":\"JSON\",\"json\":\"{\\\"fieldOne\\\":\\\"valueOne\\\",\\\"fieldTwo\\\":\\\"valueTwo\\\"}\"}"));
    }

    @Test
    public void shouldSerializeJsonBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(json("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}")),
                is("{\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithDefaultMatchType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(json("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MatchType.ONLY_MATCHING_FIELDS)),
                is("{\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithMatchType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(json("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MatchType.STRICT)),
                is("{\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\",\"matchType\":\"STRICT\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithNoneDefaultMatchTypeAndCharset() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(json("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", StandardCharsets.UTF_16, MatchType.STRICT)),
                is("{\"contentType\":\"application/json; charset=utf-16\",\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\",\"matchType\":\"STRICT\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithNoneDefaultMatchTypeAndContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(json("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MediaType.JSON_UTF_8, MatchType.STRICT)),
                is("{\"contentType\":\"application/json; charset=utf-8\",\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\",\"matchType\":\"STRICT\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithMatchTypeWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(json("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MatchType.STRICT))),
                is("{\"not\":true,\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\",\"matchType\":\"STRICT\"}"));
    }
}
