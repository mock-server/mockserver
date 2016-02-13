package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.XmlBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Not.not;

public class XmlBodySerializerTest {

    @Test
    public void shouldSerializeXmlBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new XmlBody("<some><xml></xml></some>")),
                is("{\"type\":\"XML\",\"xml\":\"<some><xml></xml></some>\"}"));
    }

    @Test
    public void shouldSerializeXmlBodyWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new XmlBody("<some><xml></xml></some>"))),
                is("{\"not\":true,\"type\":\"XML\",\"xml\":\"<some><xml></xml></some>\"}"));
    }

}