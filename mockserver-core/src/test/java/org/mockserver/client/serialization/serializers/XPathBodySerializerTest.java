package org.mockserver.client.serialization.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.XPathBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class XPathBodySerializerTest {

    @Test
    public void shouldSerializeXPathBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new XPathBody("\\some\\xpath")),
                is("{\"type\":\"XPATH\",\"value\":\"\\\\some\\\\xpath\"}"));
    }

}