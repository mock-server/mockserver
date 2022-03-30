package org.mockserver.serialization.serializers.collections;

import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Test;
import org.mockserver.model.Cookies;
import org.mockserver.serialization.ObjectMapperFactory;

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
public class CookiesSerializerTest {

    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true, false);

    @Test
    public void shouldSerializeCompleteObject() throws IOException {
        // given
        String expectedString = "{" + NEW_LINE +
            "  \"some_name\" : \"some_value\"," + NEW_LINE +
            "  \"some_other_name\" : \"!some_other_value\"" + NEW_LINE +
            "}";

        // when
        String actualString = objectWriter
            .writeValueAsString(new Cookies(
                cookie(string("some_name"), string("some_value")),
                cookie(string("some_other_name"), string("some_value")),
                cookie(string("some_other_name"), not("some_other_value"))
            ));

        // then
        assertThat(actualString, is(expectedString));
    }

}
