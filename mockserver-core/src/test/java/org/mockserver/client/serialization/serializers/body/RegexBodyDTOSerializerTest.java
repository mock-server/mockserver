package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.RegexBodyDTO;
import org.mockserver.model.RegexBody;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class RegexBodyDTOSerializerTest {

    @Test
    public void shouldSerializeRegexBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new RegexBodyDTO(new RegexBody("some[a-zA-Z]*"))),
                is("{\"type\":\"REGEX\",\"regex\":\"some[a-zA-Z]*\"}"));
    }

    @Test
    public void shouldSerializeRegexBodyDTOWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writeValueAsString(new RegexBodyDTO(new RegexBody("some[a-zA-Z]*"), true)),
                is("{\"not\":true,\"type\":\"REGEX\",\"regex\":\"some[a-zA-Z]*\"}"));
    }

}