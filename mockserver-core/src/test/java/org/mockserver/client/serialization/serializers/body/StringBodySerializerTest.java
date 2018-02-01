package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.MediaType;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.StringBodyDTO;
import org.mockserver.model.StringBody;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Not.not;

public class StringBodySerializerTest {

    @Test
    public void shouldSerializeStringBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBody("string_body")),
                is("\"string_body\""));
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBody("string_body", false)),
            is("\"string_body\""));
    }

    @Test
    public void shouldSerializeStringBodyDTOWithSubString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBody("string_body", true)),
            is("{\"type\":\"STRING\",\"string\":\"string_body\",\"subString\":true}"));
    }

    @Test
    public void shouldSerializeStringBodyWithCharset() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBody("string_body", StandardCharsets.UTF_16)),
                is("{\"type\":\"STRING\",\"string\":\"string_body\",\"contentType\":\"text/plain; charset=utf-16\"}"));
    }

    @Test
    public void shouldSerializeStringBodyWithContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBody("string_body", MediaType.ATOM_UTF_8)),
                is("{\"type\":\"STRING\",\"string\":\"string_body\",\"contentType\":\"application/atom+xml; charset=utf-8\"}"));
    }

    @Test
    public void shouldSerializeStringBodyWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new StringBody("string_body"))),
                is("{\"not\":true,\"type\":\"STRING\",\"string\":\"string_body\"}"));
    }

    @Test
    public void shouldSerializeStringBodyWithCharsetAndNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new StringBody("string_body", StandardCharsets.UTF_16))),
                is("{\"not\":true,\"type\":\"STRING\",\"string\":\"string_body\",\"contentType\":\"text/plain; charset=utf-16\"}"));
    }

    @Test
    public void shouldSerializeStringBodyWithContentTypeAndNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new StringBody("string_body", MediaType.ATOM_UTF_8))),
                is("{\"not\":true,\"type\":\"STRING\",\"string\":\"string_body\",\"contentType\":\"application/atom+xml; charset=utf-8\"}"));
    }
}
