package org.mockserver.serialization.serializers.collections;

import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Test;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.ParameterStyle;
import org.mockserver.model.Parameters;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.KeyMatchStyle.MATCHING_KEY;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;

/**
 * @author jamesdbloom
 */
public class ParametersSerializerTest {

    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true);

    @Test
    public void shouldSerializeCompleteObject() throws IOException {
        // given
        String expectedString = "{" + NEW_LINE +
            "  \"some_name\" : [ \"some_value\", \"some_other_value\" ]," + NEW_LINE +
            "  \"some_other_name\" : [ \"some_value\", \"!some_other_value\" ]" + NEW_LINE +
            "}";

        // when
        String actualString = objectWriter
            .writeValueAsString(new Parameters(
                param(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
                param(string("some_other_name"), string("some_value")),
                param(string("some_other_name"), not("some_other_value"))
            ));

        // then
        assertThat(actualString, is(expectedString));
    }

    @Test
    public void shouldSerializeCompleteObjectWithMatchingKeyKetMatch() throws IOException {
        // given
        String expectedString = "{" + NEW_LINE +
            "  \"keyMatchStyle\" : \"MATCHING_KEY\"," + NEW_LINE +
            "  \"some_name\" : [ \"some_value\", \"some_other_value\" ]," + NEW_LINE +
            "  \"some_other_name\" : [ \"some_value\", \"!some_other_value\" ]" + NEW_LINE +
            "}";

        // when
        String actualString = objectWriter
            .writeValueAsString(new Parameters(
                param(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
                param(string("some_other_name"), string("some_value")),
                param(string("some_other_name"), not("some_other_value"))
            ).withKeyMatchStyle(KeyMatchStyle.MATCHING_KEY));

        // then
        assertThat(actualString, is(expectedString));
    }

    @Test
    public void shouldSerializeCompleteObjectWithSubSetKeyMatch() throws IOException {
        // given
        String expectedString = "{" + NEW_LINE +
            "  \"some_name\" : [ \"some_value\", \"some_other_value\" ]," + NEW_LINE +
            "  \"some_other_name\" : [ \"some_value\", \"!some_other_value\" ]" + NEW_LINE +
            "}";

        // when
        String actualString = objectWriter
            .writeValueAsString(new Parameters(
                param(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))),
                param(string("some_other_name"), string("some_value")),
                param(string("some_other_name"), not("some_other_value"))
            ).withKeyMatchStyle(KeyMatchStyle.SUB_SET));

        // then
        assertThat(actualString, is(expectedString));
    }

    @Test
    public void shouldDeserializeMapWithParameterStyle() throws IOException {
        // given
        String expectedString = "{" + NEW_LINE +
            "  \"keyMatchStyle\" : \"MATCHING_KEY\"," + NEW_LINE +
            "  \"some_name\" : {" + NEW_LINE +
            "    \"parameterStyle\" : \"SPACE_DELIMITED\"," + NEW_LINE +
            "    \"values\" : [ \"some_value\", \"some_other_value\" ]" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"some_other_name\" : {" + NEW_LINE +
            "    \"parameterStyle\" : \"MATRIX\"," + NEW_LINE +
            "    \"values\" : [ \"some_value\" ]" + NEW_LINE +
            "  }," + NEW_LINE +
            "  \"some_other_name_two\" : [ \"!some_other_value\" ]" + NEW_LINE +
            "}";

        // when
        String actualString = objectWriter
            .writeValueAsString(new Parameters(
                param(string("some_name"), Arrays.asList(string("some_value"), string("some_other_value"))).withStyle(ParameterStyle.SPACE_DELIMITED),
                param(string("some_other_name"), string("some_value")).withStyle(ParameterStyle.MATRIX),
                param(string("some_other_name_two"), not("some_other_value"))
            ).withKeyMatchStyle(MATCHING_KEY));

        // then
        assertThat(actualString, is(expectedString));
    }

}
