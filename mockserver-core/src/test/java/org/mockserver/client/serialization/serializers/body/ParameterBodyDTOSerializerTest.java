package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.hamcrest.core.Is;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.BodyDTO;
import org.mockserver.client.serialization.model.ParameterBodyDTO;
import org.mockserver.client.serialization.model.ParameterBodyDTO;
import org.mockserver.model.ParameterBody;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;

public class ParameterBodyDTOSerializerTest {

    @Test
    public void shouldSerializeParameterBodyDTO() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(new ParameterBodyDTO(params(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                ))),
                is("{" + System.getProperty("line.separator") +
                        "  \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                        "  \"parameters\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"queryStringParameterOneName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"queryStringParameterOneValueOne\", \"queryStringParameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                        "  }, {" + System.getProperty("line.separator") +
                        "    \"name\" : \"queryStringParameterTwoName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"queryStringParameterTwoValue\" ]" + System.getProperty("line.separator") +
                        "  } ]" + System.getProperty("line.separator") +
                        "}"));
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithNot() throws JsonProcessingException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(new ParameterBodyDTO(params(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                ), true)),
                is("{" + System.getProperty("line.separator") +
                        "  \"not\" : true," + System.getProperty("line.separator") +
                        "  \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                        "  \"parameters\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"queryStringParameterOneName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"queryStringParameterOneValueOne\", \"queryStringParameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                        "  }, {" + System.getProperty("line.separator") +
                        "    \"name\" : \"queryStringParameterTwoName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"queryStringParameterTwoValue\" ]" + System.getProperty("line.separator") +
                        "  } ]" + System.getProperty("line.separator") +
                        "}"));
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithAllNottedParameterKeys() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(new ParameterBodyDTO(params(
                        param(not("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                        param(not("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
                ))),
                is("{" + System.getProperty("line.separator") +
                        "  \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                        "  \"parameters\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"!queryStringParameterOneName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"!queryStringParameterOneValueOne\", \"!queryStringParameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                        "  }, {" + System.getProperty("line.separator") +
                        "    \"name\" : \"!queryStringParameterTwoName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"!queryStringParameterTwoValue\" ]" + System.getProperty("line.separator") +
                        "  } ]" + System.getProperty("line.separator") +
                        "}"));
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithAllNottedParameterValues() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(new ParameterBodyDTO(params(
                        param(not("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                        param(not("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
                ))),
                is("{" + System.getProperty("line.separator") +
                        "  \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                        "  \"parameters\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"!queryStringParameterOneName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"!queryStringParameterOneValueOne\", \"!queryStringParameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                        "  }, {" + System.getProperty("line.separator") +
                        "    \"name\" : \"!queryStringParameterTwoName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"!queryStringParameterTwoValue\" ]" + System.getProperty("line.separator") +
                        "  } ]" + System.getProperty("line.separator") +
                        "}"));
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithAllNottedParameterKeysAndValue() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(new ParameterBodyDTO(params(
                        param(not("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                        param(not("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
                ))),
                is("{" + System.getProperty("line.separator") +
                        "  \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                        "  \"parameters\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"!queryStringParameterOneName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"!queryStringParameterOneValueOne\", \"!queryStringParameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                        "  }, {" + System.getProperty("line.separator") +
                        "    \"name\" : \"!queryStringParameterTwoName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"!queryStringParameterTwoValue\" ]" + System.getProperty("line.separator") +
                        "  } ]" + System.getProperty("line.separator") +
                        "}"));
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithAMixtureOfNottedAndStringParameterKeysAndValue() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(new ParameterBodyDTO(params(
                        param(not("queryStringParameterOneName"), string("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                        param(string("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
                ))),
                is("{" + System.getProperty("line.separator") +
                        "  \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                        "  \"parameters\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"!queryStringParameterOneName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"queryStringParameterOneValueOne\", \"!queryStringParameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                        "  }, {" + System.getProperty("line.separator") +
                        "    \"name\" : \"queryStringParameterTwoName\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"!queryStringParameterTwoValue\" ]" + System.getProperty("line.separator") +
                        "  } ]" + System.getProperty("line.separator") +
                        "}"));
    }

}