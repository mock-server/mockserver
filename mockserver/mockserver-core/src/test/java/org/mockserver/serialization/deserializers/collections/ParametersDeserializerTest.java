package org.mockserver.serialization.deserializers.collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Test;
import org.mockserver.model.Parameter;
import org.mockserver.model.ParameterStyle;
import org.mockserver.model.Parameters;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;
import java.util.Arrays;

import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.KeyMatchStyle.MATCHING_KEY;
import static org.mockserver.model.KeyMatchStyle.SUB_SET;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;

/**
 * @author jamesdbloom
 */
public class ParametersDeserializerTest {

    private final ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true, false);
    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    @Test
    public void shouldSerializeThenDeserializer() throws IOException {
        // given
        String serializedParameters = objectWriter
            .writeValueAsString(new Parameters(
                param(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
                param(string("some_other_name"), string("some_value")),
                param(string("some_other_name"), not("some_other_value"))
            ));

        // when
        Parameters actualParameters = objectMapper.readValue(serializedParameters, Parameters.class);

        // then
        assertThat(actualParameters, is(new Parameters(
            param(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
            param(string("some_other_name"), string("some_value")),
            param(string("some_other_name"), not("some_other_value"))
        )));
    }

    @Test
    public void shouldDeserializeMap() throws IOException {
        // given
        ObjectMapper objectMapper = this.objectMapper;
        Parameters expectedParameters = new Parameters(
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
        Parameters expectedParameters = new Parameters(
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

    @Test
    public void shouldDeserializeMapWithMatchingKeyKetMatch() throws IOException {
        // given
        ObjectMapper objectMapper = this.objectMapper;
        Parameters expectedParameters = new Parameters(
            param(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
            param(string("some_other_name"), string("some_value")),
            param(string("some_other_name"), not("some_other_value"))
        ).withKeyMatchStyle(MATCHING_KEY);

        // when
        Parameters actualParameters = objectMapper.readValue("{" + NEW_LINE +
            "  \"keyMatchStyle\" : \"MATCHING_KEY\"," + NEW_LINE +
            "  \"some_name\" : [ \"some_value\", \"some_other_value\" ]," + NEW_LINE +
            "  \"some_other_name\" : [ \"some_value\", \"!some_other_value\" ]" + NEW_LINE +
            "}", Parameters.class);

        // then
        assertThat(actualParameters, is(expectedParameters));
    }

    @Test
    public void shouldDeserializeMapWithSubSetKetMatch() throws IOException {
        // given
        ObjectMapper objectMapper = this.objectMapper;
        Parameters expectedParameters = new Parameters(
            param(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
            param(string("some_other_name"), string("some_value")),
            param(string("some_other_name"), not("some_other_value"))
        ).withKeyMatchStyle(SUB_SET);

        // when
        Parameters actualParameters = objectMapper.readValue("{" + NEW_LINE +
            "  \"keyMatchStyle\" : \"SUB_SET\"," + NEW_LINE +
            "  \"some_name\" : [ \"some_value\", \"some_other_value\" ]," + NEW_LINE +
            "  \"some_other_name\" : [ \"some_value\", \"!some_other_value\" ]" + NEW_LINE +
            "}", Parameters.class);

        // then
        assertThat(actualParameters, is(expectedParameters));
    }

    @Test
    public void shouldDeserializeMapWithParameterStyle() throws IOException {
        // given
        ObjectMapper objectMapper = this.objectMapper;
        Parameters expectedParameters = new Parameters(
            param(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))).withStyle(ParameterStyle.SPACE_DELIMITED),
            param(string("some_other_name"), string("some_value")).withStyle(ParameterStyle.MATRIX),
            param(string("some_other_name_two"), string("some_other_value"))
        ).withKeyMatchStyle(MATCHING_KEY);

        // when
        Parameters actualParameters = objectMapper.readValue("{" + NEW_LINE +
            "  \"keyMatchStyle\" : \"MATCHING_KEY\"," + NEW_LINE +
            "  \"some_name\" : {" + NEW_LINE +
            "    \"parameterStyle\" : \"SPACE_DELIMITED\"," + NEW_LINE +
            "    \"values\" : [ \"some_value\", \"some_other_value\" ]" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"some_other_name\" : {" + NEW_LINE +
            "    \"parameterStyle\" : \"MATRIX\"," + NEW_LINE +
            "    \"values\" : [ \"some_value\" ]" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"some_other_name_two\" : [ \"some_other_value\" ]" + NEW_LINE +
            "}", Parameters.class);

        // then
        assertThat(actualParameters, is(expectedParameters));
        for (Parameter entry : actualParameters.getEntries()) {
            switch (entry.getName().getValue()) {
                case "some_name":
                    assertThat(entry.getName().getParameterStyle(), is(ParameterStyle.SPACE_DELIMITED));
                    break;
                case "some_other_name":
                    assertThat(entry.getName().getParameterStyle(), is(ParameterStyle.MATRIX));
                    break;
                case "some_other_name_two":
                    assertThat(entry.getName().getParameterStyle(), is(nullValue()));
                    break;
                default:
                    fail("incorrect key " + entry.getName().getValue());
                    break;
            }
        }
    }

}
