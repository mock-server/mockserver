package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.model.MediaType;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.StringBodyDTO;
import org.mockserver.model.StringBody;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.Not.not;

public class StringBodyDTOSerializerTest {

    @Test
    public void shouldSerializeStringBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBodyDTO(new StringBody("string_body"))),
                is("\"string_body\""));
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBodyDTO(new StringBody("string_body", null, false, null))),
            is("\"string_body\""));
    }

    @Test
    public void shouldSerializeStringBodyDTOAsObjectPrettyPrintedWithoutDefaultFields() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper(true, false).writeValueAsString(new StringBodyDTO(new StringBody("string_body"))),
            is("\"string_body\""));
    }

    @Test
    public void shouldSerializeStringBodyDTOAsObjectPrettyPrintedWithDefaultFields() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper(true, true).writeValueAsString(new StringBodyDTO(new StringBody("string_body"))),
            is("{" + NEW_LINE +
                "  \"type\" : \"STRING\"," + NEW_LINE +
                "  \"string\" : \"string_body\"," + NEW_LINE +
                "  \"rawBytes\" : \"c3RyaW5nX2JvZHk=\"" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldSerializeStringBodyDTOWithSubString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBodyDTO(new StringBody("string_body", null, true, null))),
            is("{\"type\":\"STRING\",\"string\":\"string_body\",\"rawBytes\":\"c3RyaW5nX2JvZHk=\",\"subString\":true}"));
    }

    @Test
    public void shouldSerializeStringBodyDTOWithCharset() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBodyDTO(new StringBody("string_body", MediaType.PLAIN_TEXT_UTF_8))),
                is("{\"type\":\"STRING\",\"string\":\"string_body\",\"rawBytes\":\"c3RyaW5nX2JvZHk=\",\"contentType\":\"text/plain; charset=utf-8\"}"));
    }

    @Test
    public void shouldSerializeStringBodyDTOWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBodyDTO(not(new StringBody("string_body")))),
                is("{\"not\":true,\"type\":\"STRING\",\"string\":\"string_body\",\"rawBytes\":\"c3RyaW5nX2JvZHk=\"}"));
    }

    @Test
    public void shouldSerializeStringBodyDTOWithOptional() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBodyDTO(new StringBody("string_body")).withOptional(true)),
                is("{\"optional\":true,\"type\":\"STRING\",\"string\":\"string_body\",\"rawBytes\":\"c3RyaW5nX2JvZHk=\"}"));
    }

    @Test
    public void shouldSerializeStringBodyDTOWithCharsetAndNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBodyDTO(not(new StringBody("string_body", MediaType.PLAIN_TEXT_UTF_8)))),
                is("{\"not\":true,\"type\":\"STRING\",\"string\":\"string_body\",\"rawBytes\":\"c3RyaW5nX2JvZHk=\",\"contentType\":\"text/plain; charset=utf-8\"}"));
    }

}
