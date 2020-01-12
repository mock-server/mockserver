package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.model.MediaType;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.StringBodyDTO;
import org.mockserver.model.StringBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Not.not;

public class StringBodyDTOSerializerTest {

    @Test
    public void shouldSerializeStringBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBodyDTO(new StringBody("string_body"))),
                is("\"string_body\""));
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBodyDTO(new StringBody("string_body", null, false, (MediaType) null))),
            is("\"string_body\""));
    }

    @Test
    public void shouldSerializeStringBodyDTOWithSubString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBodyDTO(new StringBody("string_body", null, true, (MediaType) null))),
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
    public void shouldSerializeStringBodyDTOWithCharsetAndNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBodyDTO(not(new StringBody("string_body", MediaType.PLAIN_TEXT_UTF_8)))),
                is("{\"not\":true,\"type\":\"STRING\",\"string\":\"string_body\",\"rawBytes\":\"c3RyaW5nX2JvZHk=\",\"contentType\":\"text/plain; charset=utf-8\"}"));
    }

}
