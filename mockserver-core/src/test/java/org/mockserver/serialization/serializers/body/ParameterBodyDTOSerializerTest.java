package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.ParameterBodyDTO;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;

public class ParameterBodyDTOSerializerTest {

    @Test
    public void shouldSerializeParameterBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper(true).writeValueAsString(new ParameterBodyDTO(params(
            param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
            param("queryStringParameterTwoName", "queryStringParameterTwoValue")
            ))),
            is("{" + NEW_LINE +
                "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "  \"parameters\" : {" + NEW_LINE +
                "    \"queryStringParameterOneName\" : [ \"queryStringParameterOneValueOne\", \"queryStringParameterOneValueTwo\" ]," + NEW_LINE +
                "    \"queryStringParameterTwoName\" : [ \"queryStringParameterTwoValue\" ]" + NEW_LINE +
                "  }" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper(true).writeValueAsString(new ParameterBodyDTO(params(
            param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
            param("queryStringParameterTwoName", "queryStringParameterTwoValue")
            ), true)),
            is("{" + NEW_LINE +
                "  \"not\" : true," + NEW_LINE +
                "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "  \"parameters\" : {" + NEW_LINE +
                "    \"queryStringParameterOneName\" : [ \"queryStringParameterOneValueOne\", \"queryStringParameterOneValueTwo\" ]," + NEW_LINE +
                "    \"queryStringParameterTwoName\" : [ \"queryStringParameterTwoValue\" ]" + NEW_LINE +
                "  }" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithAllNottedParameterKeys() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper(true).writeValueAsString(new ParameterBodyDTO(params(
            param(not("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
            param(not("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
            ))),
            is("{" + NEW_LINE +
                "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "  \"parameters\" : {" + NEW_LINE +
                "    \"!queryStringParameterOneName\" : [ \"!queryStringParameterOneValueOne\", \"!queryStringParameterOneValueTwo\" ]," + NEW_LINE +
                "    \"!queryStringParameterTwoName\" : [ \"!queryStringParameterTwoValue\" ]" + NEW_LINE +
                "  }" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithAllNottedParameterValues() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper(true).writeValueAsString(new ParameterBodyDTO(params(
            param(not("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
            param(not("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
            ))),
            is("{" + NEW_LINE +
                "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "  \"parameters\" : {" + NEW_LINE +
                "    \"!queryStringParameterOneName\" : [ \"!queryStringParameterOneValueOne\", \"!queryStringParameterOneValueTwo\" ]," + NEW_LINE +
                "    \"!queryStringParameterTwoName\" : [ \"!queryStringParameterTwoValue\" ]" + NEW_LINE +
                "  }" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithAllNottedParameterKeysAndValue() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper(true).writeValueAsString(new ParameterBodyDTO(params(
            param(not("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
            param(not("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
            ))),
            is("{" + NEW_LINE +
                "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "  \"parameters\" : {" + NEW_LINE +
                "    \"!queryStringParameterOneName\" : [ \"!queryStringParameterOneValueOne\", \"!queryStringParameterOneValueTwo\" ]," + NEW_LINE +
                "    \"!queryStringParameterTwoName\" : [ \"!queryStringParameterTwoValue\" ]" + NEW_LINE +
                "  }" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithAMixtureOfNottedAndStringParameterKeysAndValue() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper(true).writeValueAsString(new ParameterBodyDTO(params(
            param(not("queryStringParameterOneName"), string("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
            param(string("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
            ))),
            is("{" + NEW_LINE +
                "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "  \"parameters\" : {" + NEW_LINE +
                "    \"!queryStringParameterOneName\" : [ \"queryStringParameterOneValueOne\", \"!queryStringParameterOneValueTwo\" ]," + NEW_LINE +
                "    \"queryStringParameterTwoName\" : [ \"!queryStringParameterTwoValue\" ]" + NEW_LINE +
                "  }" + NEW_LINE +
                "}"));
    }

}
