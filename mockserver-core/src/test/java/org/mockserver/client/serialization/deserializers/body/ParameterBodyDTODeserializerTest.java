package org.mockserver.client.serialization.deserializers.body;

import org.hamcrest.core.Is;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.BodyDTO;
import org.mockserver.client.serialization.model.ParameterBodyDTO;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;

public class ParameterBodyDTODeserializerTest {

    @Test
    public void shouldSerializeParameterBodyDTO() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "  \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "  \"parameters\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"queryStringParameterOneName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"queryStringParameterOneValueOne\", \"queryStringParameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                "  }, {" + System.getProperty("line.separator") +
                "    \"name\" : \"queryStringParameterTwoName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"queryStringParameterTwoValue\" ]" + System.getProperty("line.separator") +
                "  } ]" + System.getProperty("line.separator") +
                "}");

        // when
        BodyDTO bodyDTO = ObjectMapperFactory.createObjectMapper().readValue(json, BodyDTO.class);

        // then
        assertThat(bodyDTO,
                Is.<BodyDTO>is(new ParameterBodyDTO(params(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                )))
        );
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithNot() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "  \"not\" : true," + System.getProperty("line.separator") +
                "  \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "  \"parameters\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"queryStringParameterOneName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"queryStringParameterOneValueOne\", \"queryStringParameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                "  }, {" + System.getProperty("line.separator") +
                "    \"name\" : \"queryStringParameterTwoName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"queryStringParameterTwoValue\" ]" + System.getProperty("line.separator") +
                "  } ]" + System.getProperty("line.separator") +
                "}");

        // when
        BodyDTO bodyDTO = ObjectMapperFactory.createObjectMapper().readValue(json, BodyDTO.class);

        // then
        assertThat(bodyDTO,
                Is.<BodyDTO>is(new ParameterBodyDTO(params(
                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                ), true))
        );
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithAllNottedParameterKeys() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "  \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "  \"parameters\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterOneName\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"values\" : [ {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterOneValueOne\"" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterOneValueTwo\"" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }, {" + System.getProperty("line.separator") +
                "    \"name\" : {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterTwoName\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"values\" : [ {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterTwoValue\"" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  } ]" + System.getProperty("line.separator") +
                "}");

        // when
        BodyDTO bodyDTO = ObjectMapperFactory.createObjectMapper().readValue(json, BodyDTO.class);

        // then
        assertThat(bodyDTO,
                Is.<BodyDTO>is(new ParameterBodyDTO(params(
                        param(not("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                        param(not("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
                )))
        );
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithAllNottedParameterValues() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "  \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "  \"parameters\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterOneName\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"values\" : [ {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterOneValueOne\"" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterOneValueTwo\"" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }, {" + System.getProperty("line.separator") +
                "    \"name\" : {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterTwoName\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"values\" : [ {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterTwoValue\"" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  } ]" + System.getProperty("line.separator") +
                "}");

        // when
        BodyDTO bodyDTO = ObjectMapperFactory.createObjectMapper().readValue(json, BodyDTO.class);

        // then
        assertThat(bodyDTO,
                Is.<BodyDTO>is(new ParameterBodyDTO(params(
                        param(not("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                        param(not("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
                )))
        );
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithAllNottedParameterKeysAndValue() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "  \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "  \"parameters\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterOneName\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"values\" : [ {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterOneValueOne\"" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterOneValueTwo\"" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }, {" + System.getProperty("line.separator") +
                "    \"name\" : {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterTwoName\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"values\" : [ {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterTwoValue\"" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  } ]" + System.getProperty("line.separator") +
                "}");

        // when
        BodyDTO bodyDTO = ObjectMapperFactory.createObjectMapper().readValue(json, BodyDTO.class);

        // then
        assertThat(bodyDTO,
                Is.<BodyDTO>is(new ParameterBodyDTO(params(
                        param(not("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                        param(not("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
                )))
        );
    }

    @Test
    public void shouldSerializeParameterBodyDTOWithAMixtureOfNottedAndStringParameterKeysAndValue() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "  \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "  \"parameters\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterOneName\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"values\" : [ \"queryStringParameterOneValueOne\", {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterOneValueTwo\"" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }, {" + System.getProperty("line.separator") +
                "    \"name\" : \"queryStringParameterTwoName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ {" + System.getProperty("line.separator") +
                "      \"not\" : true," + System.getProperty("line.separator") +
                "      \"value\" : \"queryStringParameterTwoValue\"" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  } ]" + System.getProperty("line.separator") +
                "}");

        // when
        BodyDTO bodyDTO = ObjectMapperFactory.createObjectMapper().readValue(json, BodyDTO.class);

        // then
        assertThat(bodyDTO,
                Is.<BodyDTO>is(new ParameterBodyDTO(params(
                        param(not("queryStringParameterOneName"), string("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                        param(string("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
                )))
        );
    }

}