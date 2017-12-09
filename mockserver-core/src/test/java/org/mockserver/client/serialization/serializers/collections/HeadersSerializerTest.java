package org.mockserver.client.serialization.serializers.collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.Headers;

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
public class HeadersSerializerTest {

    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    @Test
    public void shouldAllowSingleObjectForArray() throws IOException {
        // given
        String expectedString = "{" + NEW_LINE +
            "  \"some_name\" : [ \"some_value\", \"some_other_value\" ]," + NEW_LINE +
            "  \"some_other_name\" : [ \"some_value\", \"!some_other_value\" ]" + NEW_LINE +
            "}";

        // when
        String actualString = objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(new Headers().withEntries(
                header(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
                header(string("some_other_name"), string("some_value")),
                header(string("some_other_name"), not("some_other_value"))
            ));

        // then
        assertThat(actualString, is(expectedString));
    }

}
