package org.mockserver.client.serialization.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.StringBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class StringBodySerializerTest {

    @Test
    public void shouldSerializeStringBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new StringBody("string_body")),
                is("\"string_body\""));
    }

}