package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.MediaType;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.BinaryBody;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class BinaryBodySerializerTest {

    @Test
    public void shouldSerializeBinaryBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBody("some_bytes".getBytes(UTF_8))),
                is("{\"type\":\"BINARY\",\"base64Bytes\":\"c29tZV9ieXRlcw==\"}"));
    }

    @Test
    public void shouldSerializeBinaryBodyWithContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBody("some_bytes".getBytes(UTF_8), MediaType.APPLICATION_BINARY)),
                is("{\"contentType\":\"application/binary\",\"type\":\"BINARY\",\"base64Bytes\":\"c29tZV9ieXRlcw==\"}"));
    }
}
