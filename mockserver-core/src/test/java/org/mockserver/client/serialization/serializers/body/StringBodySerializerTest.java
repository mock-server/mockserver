package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Charsets;
import com.google.common.net.MediaType;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.StringBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Not.not;

public class StringBodySerializerTest {

    @Test
    public void shouldSerializeStringBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBody("string_body")),
                is("\"string_body\""));
    }

    @Test
    public void shouldSerializeStringBodyWithCharset() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBody("string_body", Charsets.UTF_16)),
                is("{\"contentType\":\"text/plain; charset=utf-16\",\"type\":\"STRING\",\"string\":\"string_body\"}"));
    }

    @Test
    public void shouldSerializeStringBodyWithContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBody("string_body", MediaType.ATOM_UTF_8)),
                is("{\"contentType\":\"application/atom+xml; charset=utf-8\",\"type\":\"STRING\",\"string\":\"string_body\"}"));
    }

    @Test
    public void shouldSerializeStringBodyWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new StringBody("string_body"))),
                is("{\"not\":true,\"type\":\"STRING\",\"string\":\"string_body\"}"));
    }

    @Test
    public void shouldSerializeStringBodyWithCharsetAndNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new StringBody("string_body", Charsets.UTF_16))),
                is("{\"not\":true,\"contentType\":\"text/plain; charset=utf-16\",\"type\":\"STRING\",\"string\":\"string_body\"}"));
    }

    @Test
    public void shouldSerializeStringBodyWithContentTypeAndNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new StringBody("string_body", MediaType.ATOM_UTF_8))),
                is("{\"not\":true,\"contentType\":\"application/atom+xml; charset=utf-8\",\"type\":\"STRING\",\"string\":\"string_body\"}"));
    }
}