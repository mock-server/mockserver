package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.JsonBodyDTO;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class JsonBodyDTOSerializerTest {

    @Test
    public void shouldSerializeJsonBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonBodyDTO(new JsonBody("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}"))),
            is("{\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\",\"rawBytes\":\"e2ZpZWxkT25lOiAidmFsdWVPbmUiLCAiZmllbGRUd28iOiAidmFsdWVUd28ifQ==\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithDefaultMatchType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonBodyDTO(new JsonBody("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MatchType.ONLY_MATCHING_FIELDS))),
            is("{\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\",\"rawBytes\":\"e2ZpZWxkT25lOiAidmFsdWVPbmUiLCAiZmllbGRUd28iOiAidmFsdWVUd28ifQ==\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithNoneDefaultMatchType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonBodyDTO(new JsonBody("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MatchType.STRICT))),
            is("{\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\",\"rawBytes\":\"e2ZpZWxkT25lOiAidmFsdWVPbmUiLCAiZmllbGRUd28iOiAidmFsdWVUd28ifQ==\",\"matchType\":\"STRICT\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithNoneDefaultMatchTypeAndCharset() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonBodyDTO(new JsonBody("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", null, (StandardCharsets.UTF_16 != null ? MediaType.create("application", "json").withCharset(StandardCharsets.UTF_16) : null), MatchType.STRICT))),
            is("{\"contentType\":\"application/json; charset=utf-16\",\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\",\"rawBytes\":\"/v8AewBmAGkAZQBsAGQATwBuAGUAOgAgACIAdgBhAGwAdQBlAE8AbgBlACIALAAgACIAZgBpAGUAbABkAFQAdwBvACIAOgAgACIAdgBhAGwAdQBlAFQAdwBvACIAfQ==\",\"matchType\":\"STRICT\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithDefaultMatchTypeAndContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonBodyDTO(new JsonBody("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", null, MediaType.JSON_UTF_8, MatchType.STRICT))),
            is("{\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\",\"rawBytes\":\"e2ZpZWxkT25lOiAidmFsdWVPbmUiLCAiZmllbGRUd28iOiAidmFsdWVUd28ifQ==\",\"matchType\":\"STRICT\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyWithNoneDefaultMatchTypeAndContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonBodyDTO(new JsonBody("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", null, MediaType.parse("application/json; charset=utf-16"), MatchType.STRICT))),
            is("{\"contentType\":\"application/json; charset=utf-16\",\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\",\"rawBytes\":\"/v8AewBmAGkAZQBsAGQATwBuAGUAOgAgACIAdgBhAGwAdQBlAE8AbgBlACIALAAgACIAZgBpAGUAbABkAFQAdwBvACIAOgAgACIAdgBhAGwAdQBlAFQAdwBvACIAfQ==\",\"matchType\":\"STRICT\"}"));
    }

    @Test
    public void shouldSerializeJsonBodyDTOWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new JsonBodyDTO(new JsonBody("{fieldOne: \"valueOne\", \"fieldTwo\": \"valueTwo\"}", MatchType.STRICT), true)),
            is("{\"not\":true,\"type\":\"JSON\",\"json\":\"{fieldOne: \\\"valueOne\\\", \\\"fieldTwo\\\": \\\"valueTwo\\\"}\",\"rawBytes\":\"e2ZpZWxkT25lOiAidmFsdWVPbmUiLCAiZmllbGRUd28iOiAidmFsdWVUd28ifQ==\",\"matchType\":\"STRICT\"}"));
    }
}
