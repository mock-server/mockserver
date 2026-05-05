package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.model.MediaType;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.model.StringBody;
import org.mockserver.serialization.model.StringBodyDTO;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Not.not;

public class StringBodySerializerTest {

    @Test
    public void shouldSerializeStringBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBody("string_body")),
                is("\"string_body\""));
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBody("string_body", null, false, null)),
            is("\"string_body\""));
    }

    @Test
    public void shouldSerializeStringBodyAsObjectPrettyPrintedWithoutDefaultFields() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper(true, false).writeValueAsString(new StringBody("string_body")),
            is("\"string_body\""));
    }

    @Test
    public void shouldSerializeStringBodyAsObjectPrettyPrintedWithDefaultFields() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper(true, true).writeValueAsString(new StringBody("string_body")),
            is("{" + NEW_LINE +
                "  \"type\" : \"STRING\"," + NEW_LINE +
                "  \"string\" : \"string_body\"" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldSerializeStringBodyWithSubString() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBody("string_body", null, true, null)),
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
    public void shouldSerializeStringBodyWithOptional() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBody("string_body").withOptional(true)),
                is("{\"optional\":true,\"type\":\"STRING\",\"string\":\"string_body\"}"));
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
