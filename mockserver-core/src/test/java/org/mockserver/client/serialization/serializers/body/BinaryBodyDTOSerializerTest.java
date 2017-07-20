package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.MediaType;
import org.junit.Test;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.BinaryBodyDTO;
import org.mockserver.model.BinaryBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class BinaryBodyDTOSerializerTest {

    @Test
    public void shouldSerializeBinaryBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBodyDTO(new BinaryBody("someBytes".getBytes()))),
                is("{\"type\":\"BINARY\",\"base64Bytes\":\"" + Base64Converter.bytesToBase64String("someBytes".getBytes()) + "\"}"));
    }

    @Test
    public void shouldSerializeBinaryBodyDTOWithContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBodyDTO(new BinaryBody("someBytes".getBytes(), MediaType.ANY_VIDEO_TYPE))),
                is("{\"type\":\"BINARY\",\"base64Bytes\":\"" + Base64Converter.bytesToBase64String("someBytes".getBytes()) + "\",\"contentType\":\"video/*\"}"));
    }

    @Test
    public void shouldSerializeBinaryBodyDTOWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBodyDTO(new BinaryBody("someBytes".getBytes()), true)),
                is("{\"not\":true,\"type\":\"BINARY\",\"base64Bytes\":\"" + Base64Converter.bytesToBase64String("someBytes".getBytes()) + "\"}"));
    }

    @Test
    public void shouldSerializeBinaryBodyDTOWithNotWithContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBodyDTO(new BinaryBody("someBytes".getBytes(), MediaType.ANY_AUDIO_TYPE), true)),
                is("{\"not\":true,\"type\":\"BINARY\",\"base64Bytes\":\"" + Base64Converter.bytesToBase64String("someBytes".getBytes()) + "\",\"contentType\":\"audio/*\"}"));
    }

}