package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.RegexBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Not.not;

public class RegexBodySerializerTest {

    @Test
    public void shouldSerializeRegexBody() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new RegexBody("some[a-zA-Z]*")),
                is("{\"type\":\"REGEX\",\"regex\":\"some[a-zA-Z]*\"}"));
    }

    @Test
    public void shouldSerializeRegexBodyWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(not(new RegexBody("some[a-zA-Z]*"))),
                is("{\"not\":true,\"type\":\"REGEX\",\"regex\":\"some[a-zA-Z]*\"}"));
    }

}