package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.XmlBodyDTO;
import org.mockserver.model.XmlBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class XmlBodyDTOSerializerTest {

    @Test
    public void shouldSerializeXmlBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>"))),
                is("{\"type\":\"XML\",\"xml\":\"<some><xml></xml></some>\"}"));
    }

    @Test
    public void shouldSerializeXmlBodyDTOWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>"), true)),
                is("{\"not\":true,\"type\":\"XML\",\"xml\":\"<some><xml></xml></some>\"}"));
    }

}