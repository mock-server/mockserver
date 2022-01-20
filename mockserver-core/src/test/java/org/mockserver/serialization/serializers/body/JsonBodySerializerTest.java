package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.MediaType;
import org.mockserver.serialization.ObjectMapperFactory;

import java.nio.charset.StandardCharsets;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.Not.not;

public class JsonBodySerializerTest {

    public static class TestObject {
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
        String actual = ObjectMapperFactory.createObjectMapper().writeValueAsString(json(new TestObject()));
        String expectedCase1 = "{\"type\":\"JSON\",\"json\":{\"fieldOne\":\"valueOne\",\"fieldTwo\":\"valueTwo\"}}";
        String expectedCase2 = "{\"type\":\"JSON\",\"json\":{\"fieldTwo\":\"valueTwo\",\"fieldOne\":\"valueOne\"}}";
        assertTrue(actual.equals(expectedCase1) || actual.equals(expectedCase2));
    }

    @Test
    public void shouldSerializeJsonBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(json("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}")),
            is("{\"type\":\"JSON\",\"json\":{\"fieldOne\":\"valueOne\",\"fieldTwo\":\"valueTwo\"}}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithDefaultMatchType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(json("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MatchType.ONLY_MATCHING_FIELDS)),
            is("{\"type\":\"JSON\",\"json\":{\"fieldOne\":\"valueOne\",\"fieldTwo\":\"valueTwo\"}}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithMatchType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(json("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MatchType.STRICT)),
            is("{\"type\":\"JSON\",\"json\":{\"fieldOne\":\"valueOne\",\"fieldTwo\":\"valueTwo\"},\"matchType\":\"STRICT\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithNoneDefaultMatchTypeAndCharset() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(json("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", StandardCharsets.UTF_16, MatchType.STRICT)),
            is("{\"contentType\":\"application/json; charset=utf-16\",\"type\":\"JSON\",\"json\":{\"fieldOne\":\"valueOne\",\"fieldTwo\":\"valueTwo\"},\"matchType\":\"STRICT\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithDefaultMatchTypeAndContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(json("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MediaType.JSON_UTF_8, MatchType.STRICT)),
            is("{\"type\":\"JSON\",\"json\":{\"fieldOne\":\"valueOne\",\"fieldTwo\":\"valueTwo\"},\"matchType\":\"STRICT\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithNoneDefaultMatchTypeAndContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(json("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MediaType.parse("application/json; charset=utf-16"), MatchType.STRICT)),
            is("{\"contentType\":\"application/json; charset=utf-16\",\"type\":\"JSON\",\"json\":{\"fieldOne\":\"valueOne\",\"fieldTwo\":\"valueTwo\"},\"matchType\":\"STRICT\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithMatchTypeWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(json("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MatchType.STRICT))),
            is("{\"not\":true,\"type\":\"JSON\",\"json\":{\"fieldOne\":\"valueOne\",\"fieldTwo\":\"valueTwo\"},\"matchType\":\"STRICT\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithMatchTypeWithOptional() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(json("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MatchType.STRICT).withOptional(true)),
            is("{\"optional\":true,\"type\":\"JSON\",\"json\":{\"fieldOne\":\"valueOne\",\"fieldTwo\":\"valueTwo\"},\"matchType\":\"STRICT\"}"));
    }
}
