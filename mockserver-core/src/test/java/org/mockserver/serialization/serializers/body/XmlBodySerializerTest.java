package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.model.MediaType;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.model.XmlBody;

import java.nio.charset.StandardCharsets;

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

    @Test
    public void shouldSerializeXmlBodyWithContentType() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new XmlBody("<some><xml></xml></some>", MediaType.ATOM_UTF_8)),
                is("{\"contentType\":\"application/atom+xml; charset=utf-8\",\"type\":\"XML\",\"xml\":\"<some><xml></xml></some>\"}"));
    }

    @Test
    public void shouldSerializeXmlBodyWithCharsetAndNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new XmlBody("<some><xml></xml></some>", StandardCharsets.UTF_16))),
                is("{\"not\":true,\"contentType\":\"application/xml; charset=utf-16\",\"type\":\"XML\",\"xml\":\"<some><xml></xml></some>\"}"));
    }

    @Test
    public void shouldSerializeXmlBodyWithContentTypeAndNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new XmlBody("<some><xml></xml></some>", MediaType.ATOM_UTF_8))),
                is("{\"not\":true,\"contentType\":\"application/atom+xml; charset=utf-8\",\"type\":\"XML\",\"xml\":\"<some><xml></xml></some>\"}"));
    }
}
