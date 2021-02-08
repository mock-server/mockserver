package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;

import org.junit.Test;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.model.XPathBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Not.not;

public class XPathBodySerializerTest {

    @Test
    public void shouldSerializeXPathBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new XPathBody("\\some\\xpath")),
                is("{\"type\":\"XPATH\",\"xpath\":\"\\\\some\\\\xpath\"}"));
    }

    @Test
    public void shouldSerializeXPathBodyWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new XPathBody("\\some\\xpath"))),
                is("{\"not\":true,\"type\":\"XPATH\",\"xpath\":\"\\\\some\\\\xpath\"}"));
    }

    @Test
    public void shouldSerializeXPathBodyWithOptional() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new XPathBody("\\some\\xpath").withOptional(true)),
                is("{\"optional\":true,\"type\":\"XPATH\",\"xpath\":\"\\\\some\\\\xpath\"}"));
    }


    @Test
    public void shouldSerializeXPathBodyWithOptionalAndNamespacePrefixes() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new XPathBody("\\some\\xpath", ImmutableMap.of("foo", "http://foo")).withOptional(true)),
                is("{\"optional\":true,\"type\":\"XPATH\",\"xpath\":\"\\\\some\\\\xpath\",\"namespacePrefixes\":{\"foo\":\"http://foo\"}}"));
    }

}