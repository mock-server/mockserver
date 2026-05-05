package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.model.MediaType;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.model.BinaryBody;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.model.Not.not;

public class BinaryBodySerializerTest {

    @Test
    public void shouldSerializeBinaryBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBody("some_bytes".getBytes(UTF_8))),
                is("{\"type\":\"BINARY\",\"base64Bytes\":\"c29tZV9ieXRlcw==\"}"));
    }

    @Test
    public void shouldSerializeBinaryBodyWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new BinaryBody("some_bytes".getBytes(UTF_8)))),
                is("{\"not\":true,\"type\":\"BINARY\",\"base64Bytes\":\"c29tZV9ieXRlcw==\"}"));
    }

    @Test
    public void shouldSerializeBinaryBodyWithOptional() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBody("some_bytes".getBytes(UTF_8)).withOptional(true)),
                is("{\"optional\":true,\"type\":\"BINARY\",\"base64Bytes\":\"c29tZV9ieXRlcw==\"}"));
    }

    @Test
    public void shouldSerializeBinaryBodyWithContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBody("some_bytes".getBytes(UTF_8), MediaType.APPLICATION_BINARY)),
                is("{\"contentType\":\"application/binary\",\"type\":\"BINARY\",\"base64Bytes\":\"c29tZV9ieXRlcw==\"}"));
    }
}
