package org.mockserver.serialization.deserializers.collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.model.Cookies;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CookiesDeserializerTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    @Test
    public void shouldSerializeThenDeserializer() throws IOException {
        // given
        String serializedHeaders = objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(new Cookies().withEntries(
                cookie(string("some_name"), string("some_value")),
                cookie(string("some_other_name"), not("some_other_value"))
            ));

        // when
        Cookies actualHeaders = objectMapper.readValue(serializedHeaders, Cookies.class);

        // then
        assertThat(actualHeaders, is(new Cookies().withEntries(
            cookie(string("some_name"), string("some_value")),
            cookie(string("some_other_name"), not("some_other_value"))
        )));
    }

    @Test
    public void shouldDeserializeMap() throws IOException {
        // given
        Cookies expectedHeaders = new Cookies().withEntries(
            cookie(string("some_name"), string("some_value")),
            cookie(string("some_other_name"), not("some_other_value"))
        );

        // when
        Cookies actualHeaders = objectMapper.readValue("{" + NEW_LINE +
            "  \"some_name\" : \"some_value\"," + NEW_LINE +
            "  \"some_other_name\" : \"!some_other_value\"" + NEW_LINE +
            "}", Cookies.class);

        // then
        assertThat(actualHeaders, is(expectedHeaders));
    }

    @Test
    public void shouldDeserializeArray() throws IOException {
        // given
        Cookies expectedHeaders = new Cookies().withEntries(
            cookie(string("some_name"), string("some_value")),
            cookie(string("some_other_name"), not("some_other_value"))
        );

        // when
        Cookies actualHeaders = objectMapper.readValue("[" + NEW_LINE +
            "    {" + NEW_LINE +
            "        \"name\": \"some_name\", " + NEW_LINE +
            "        \"value\": \"some_value\"" + NEW_LINE +
            "    }, " + NEW_LINE +
            "    {" + NEW_LINE +
            "        \"name\": \"some_other_name\", " + NEW_LINE +
            "        \"value\": \"!some_other_value\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "]", Cookies.class);

        // then
        assertThat(actualHeaders, is(expectedHeaders));
    }

}
