package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.model.XmlSchemaBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Not.not;

public class XmlSchemaBodySerializerTest {

    @Test
    public void shouldSerializeXmlBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new XmlSchemaBody("{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"integer\"}}, \"required\": [\"id\"]}")),
                is("{\"type\":\"XML_SCHEMA\",\"xmlSchema\":\"{\\\"type\\\": \\\"object\\\", \\\"properties\\\": {\\\"id\\\": {\\\"type\\\": \\\"integer\\\"}}, \\\"required\\\": [\\\"id\\\"]}\"}"));
    }

    @Test
    public void shouldSerializeXmlBodyWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new XmlSchemaBody("{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"integer\"}}, \"required\": [\"id\"]}"))),
                is("{\"not\":true,\"type\":\"XML_SCHEMA\",\"xmlSchema\":\"{\\\"type\\\": \\\"object\\\", \\\"properties\\\": {\\\"id\\\": {\\\"type\\\": \\\"integer\\\"}}, \\\"required\\\": [\\\"id\\\"]}\"}"));
    }

}