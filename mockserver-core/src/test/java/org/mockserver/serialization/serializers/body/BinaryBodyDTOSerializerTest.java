package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.MediaType;
import org.junit.Test;
import org.mockserver.serialization.Base64Converter;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.BinaryBodyDTO;
import org.mockserver.model.BinaryBody;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class BinaryBodyDTOSerializerTest {

    private final Base64Converter base64Converter = new Base64Converter();

    @Test
    public void shouldSerializeBinaryBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBodyDTO(new BinaryBody("someBytes".getBytes(UTF_8)))),
                is("{\"type\":\"BINARY\",\"base64Bytes\":\"" + base64Converter.bytesToBase64String("someBytes".getBytes(UTF_8)) + "\"}"));
    }

    @Test
    public void shouldSerializeBinaryBodyDTOWithContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBodyDTO(new BinaryBody("someBytes".getBytes(UTF_8), MediaType.ANY_VIDEO_TYPE))),
                is("{\"type\":\"BINARY\",\"base64Bytes\":\"" + base64Converter.bytesToBase64String("someBytes".getBytes(UTF_8)) + "\",\"contentType\":\"video/*\"}"));
    }

    @Test
    public void shouldSerializeBinaryBodyDTOWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBodyDTO(new BinaryBody("someBytes".getBytes(UTF_8)), true)),
                is("{\"not\":true,\"type\":\"BINARY\",\"base64Bytes\":\"" + base64Converter.bytesToBase64String("someBytes".getBytes(UTF_8)) + "\"}"));
    }

    @Test
    public void shouldSerializeBinaryBodyDTOWithNotWithContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new BinaryBodyDTO(new BinaryBody("someBytes".getBytes(UTF_8), MediaType.ANY_AUDIO_TYPE), true)),
                is("{\"not\":true,\"type\":\"BINARY\",\"base64Bytes\":\"" + base64Converter.bytesToBase64String("someBytes".getBytes(UTF_8)) + "\",\"contentType\":\"audio/*\"}"));
    }

}
