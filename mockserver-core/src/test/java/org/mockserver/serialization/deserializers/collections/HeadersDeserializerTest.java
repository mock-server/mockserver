package org.mockserver.serialization.deserializers.collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Test;
import org.mockserver.model.Headers;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class HeadersDeserializerTest {

    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true);
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    @Test
    public void shouldSerializeThenDeserializer() throws IOException {
        // given
        String serializedHeaders = objectWriter
            .writeValueAsString(new Headers().withEntries(
                header(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
                header(string("some_other_name"), string("some_value")),
                header(string("some_other_name"), not("some_other_value"))
            ));

        // when
        Headers actualHeaders = objectMapper.readValue(serializedHeaders, Headers.class);

        // then
        assertThat(actualHeaders, is(new Headers().withEntries(
            header(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
            header(string("some_other_name"), string("some_value")),
            header(string("some_other_name"), not("some_other_value"))
        )));
    }

    @Test
    public void shouldDeserializeMap() throws IOException {
        // given
        Headers expectedHeaders = new Headers().withEntries(
            header(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
            header(string("some_other_name"), string("some_value")),
            header(string("some_other_name"), not("some_other_value"))
        );

        // when
        Headers actualHeaders = objectMapper.readValue("{" + NEW_LINE +
            "  \"some_name\" : [ \"some_value\", \"some_other_value\" ]," + NEW_LINE +
            "  \"some_other_name\" : [ \"some_value\", \"!some_other_value\" ]" + NEW_LINE +
            "}", Headers.class);

        // then
        assertThat(actualHeaders, is(expectedHeaders));
    }

    @Test
    public void shouldDeserializeArray() throws IOException {
        // given
        Headers expectedHeaders = new Headers().withEntries(
            header(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
            header(string("some_other_name"), string("some_value")),
            header(string("some_other_name"), not("some_other_value"))
        );

        // when
        Headers actualHeaders = objectMapper.readValue("[" + NEW_LINE +
            "    {" + NEW_LINE +
            "        \"name\": \"some_name\", " + NEW_LINE +
            "        \"value\": [" + NEW_LINE +
            "            \"some_value\", " + NEW_LINE +
            "            \"some_other_value\"" + NEW_LINE +
            "        ]" + NEW_LINE +
            "    }, " + NEW_LINE +
            "    {" + NEW_LINE +
            "        \"name\": \"some_other_name\", " + NEW_LINE +
            "        \"value\": [" + NEW_LINE +
            "            \"some_value\", " + NEW_LINE +
            "            \"!some_other_value\"" + NEW_LINE +
            "        ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "]", Headers.class);

        // then
        assertThat(actualHeaders, is(expectedHeaders));
    }

}
