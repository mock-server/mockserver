package org.mockserver.serialization.deserializers.collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.model.Parameters;

import java.io.IOException;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;

/**
 * @author jamesdbloom
 */
public class ParametersDeserializerTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    @Test
    public void shouldSerializeThenDeserializer() throws IOException {
        // given
        String serializedParameters = objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(new Parameters().withEntries(
                param(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
                param(string("some_other_name"), string("some_value")),
                param(string("some_other_name"), not("some_other_value"))
            ));

        // when
        Parameters actualParameters = objectMapper.readValue(serializedParameters, Parameters.class);

        // then
        assertThat(actualParameters, is(new Parameters().withEntries(
            param(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
            param(string("some_other_name"), string("some_value")),
            param(string("some_other_name"), not("some_other_value"))
        )));
    }

    @Test
    public void shouldDeserializeMap() throws IOException {
        // given
        ObjectMapper objectMapper = this.objectMapper;
        Parameters expectedParameters = new Parameters().withEntries(
            param(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
            param(string("some_other_name"), string("some_value")),
            param(string("some_other_name"), not("some_other_value"))
        );

        // when
        Parameters actualParameters = objectMapper.readValue("{" + NEW_LINE +
            "  \"some_name\" : [ \"some_value\", \"some_other_value\" ]," + NEW_LINE +
            "  \"some_other_name\" : [ \"some_value\", \"!some_other_value\" ]" + NEW_LINE +
            "}", Parameters.class);

        // then
        assertThat(actualParameters, is(expectedParameters));
    }

    @Test
    public void shouldDeserializeArray() throws IOException {
        // given
        Parameters expectedParameters = new Parameters().withEntries(
            param(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
            param(string("some_other_name"), string("some_value")),
            param(string("some_other_name"), not("some_other_value"))
        );

        // when
        Parameters actualParameters = objectMapper.readValue("[" + NEW_LINE +
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
            "]", Parameters.class);

        // then
        assertThat(actualParameters, is(expectedParameters));
    }

}
