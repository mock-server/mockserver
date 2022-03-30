package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.XPathBodyDTO;
import org.mockserver.model.XPathBody;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class XPathBodyDTOSerializerTest {

    @Test
    public void shouldSerializeXPathBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new XPathBodyDTO(new XPathBody("\\some\\xpath"))),
                is("{\"type\":\"XPATH\",\"xpath\":\"\\\\some\\\\xpath\"}"));
    }

    @Test
    public void shouldSerializeXPathBodyDTOWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new XPathBodyDTO(new XPathBody("\\some\\xpath"), true)),
                is("{\"not\":true,\"type\":\"XPATH\",\"xpath\":\"\\\\some\\\\xpath\"}"));
    }

    @Test
    public void shouldSerializeXPathBodyDTOWithOptional() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new XPathBodyDTO(new XPathBody("\\some\\xpath")).withOptional(true)),
                is("{\"optional\":true,\"type\":\"XPATH\",\"xpath\":\"\\\\some\\\\xpath\"}"));
    }


    @Test
    public void shouldSerializeXPathBodyDTOWithOptionalAndNamespacePrefixes() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new XPathBodyDTO(new XPathBody("\\some\\xpath", ImmutableMap.of("foo", "http://foo"))).withOptional(true)),
                is("{\"optional\":true,\"type\":\"XPATH\",\"xpath\":\"\\\\some\\\\xpath\",\"namespacePrefixes\":{\"foo\":\"http://foo\"}}"));
    }
}