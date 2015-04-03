package org.mockserver.client.serialization.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.StringBodyDTO;
import org.mockserver.model.StringBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class StringBodyDTOSerializerTest {

    @Test
    public void shouldSerializeStringBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBodyDTO(new StringBody("string_body"))),
                is("\"string_body\""));
    }

}