package org.mockserver.client.serialization.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.RegexBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class RegexBodySerializerTest {

    @Test
    public void shouldSerializeRegexBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new RegexBody("some[a-zA-Z]*")),
                is("{\"type\":\"REGEX\",\"value\":\"some[a-zA-Z]*\"}"));
    }

}