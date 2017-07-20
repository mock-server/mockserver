package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Charsets;
import com.google.common.net.MediaType;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.BinaryBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Not.not;

public class BinaryBodySerializerTest {

    @Test
    public void shouldSerializeBinaryBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBody("some_bytes".getBytes())),
                is("{\"type\":\"BINARY\",\"base64Bytes\":\"c29tZV9ieXRlcw==\"}"));
    }

    @Test
    public void shouldSerializeBinaryBodyWithContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBody("some_bytes".getBytes(), MediaType.APPLICATION_BINARY)),
                is("{\"contentType\":\"application/binary\",\"type\":\"BINARY\",\"base64Bytes\":\"c29tZV9ieXRlcw==\"}"));
    }
}