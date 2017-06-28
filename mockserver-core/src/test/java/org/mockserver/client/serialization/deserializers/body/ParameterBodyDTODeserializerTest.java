package org.mockserver.client.serialization.deserializers.body;

import org.hamcrest.core.Is;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.BodyDTO;
import org.mockserver.client.serialization.model.ParameterBodyDTO;

import java.io.IOException;

import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;

public class ParameterBodyDTODeserializerTest {

    @Test
    public void shouldSerializeParameterBodyDTO() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
                "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "  \"parameters\" : [ {" + NEW_LINE +
                "    \"name\" : \"queryStringParameterOneName\"," + NEW_LINE +
                "    \"values\" : [ \"queryStringParameterOneValueOne\", \"queryStringParameterOneValueTwo\" ]" + NEW_LINE +
                "  }, {" + NEW_LINE +
                "    \"name\" : \"queryStringParameterTwoName\"," + NEW_LINE +
                "    \"values\" : [ \"queryStringParameterTwoValue\" ]" + NEW_LINE +
                "  } ]" + NEW_LINE +
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
        String json = ("{" + NEW_LINE +
                "  \"not\" : true," + NEW_LINE +
                "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "  \"parameters\" : [ {" + NEW_LINE +
                "    \"name\" : \"queryStringParameterOneName\"," + NEW_LINE +
                "    \"values\" : [ \"queryStringParameterOneValueOne\", \"queryStringParameterOneValueTwo\" ]" + NEW_LINE +
                "  }, {" + NEW_LINE +
                "    \"name\" : \"queryStringParameterTwoName\"," + NEW_LINE +
                "    \"values\" : [ \"queryStringParameterTwoValue\" ]" + NEW_LINE +
                "  } ]" + NEW_LINE +
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
        String json = ("{" + NEW_LINE +
                "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "  \"parameters\" : [ {" + NEW_LINE +
                "    \"name\" : {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterOneName\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"values\" : [ {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterOneValueOne\"" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterOneValueTwo\"" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }, {" + NEW_LINE +
                "    \"name\" : {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterTwoName\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"values\" : [ {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterTwoValue\"" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  } ]" + NEW_LINE +
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
        String json = ("{" + NEW_LINE +
                "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "  \"parameters\" : [ {" + NEW_LINE +
                "    \"name\" : {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterOneName\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"values\" : [ {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterOneValueOne\"" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterOneValueTwo\"" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }, {" + NEW_LINE +
                "    \"name\" : {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterTwoName\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"values\" : [ {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterTwoValue\"" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  } ]" + NEW_LINE +
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
        String json = ("{" + NEW_LINE +
                "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "  \"parameters\" : [ {" + NEW_LINE +
                "    \"name\" : {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterOneName\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"values\" : [ {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterOneValueOne\"" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterOneValueTwo\"" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }, {" + NEW_LINE +
                "    \"name\" : {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterTwoName\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"values\" : [ {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterTwoValue\"" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  } ]" + NEW_LINE +
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
        String json = ("{" + NEW_LINE +
                "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
                "  \"parameters\" : [ {" + NEW_LINE +
                "    \"name\" : {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterOneName\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"values\" : [ \"queryStringParameterOneValueOne\", {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterOneValueTwo\"" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }, {" + NEW_LINE +
                "    \"name\" : \"queryStringParameterTwoName\"," + NEW_LINE +
                "    \"values\" : [ {" + NEW_LINE +
                "      \"not\" : true," + NEW_LINE +
                "      \"value\" : \"queryStringParameterTwoValue\"" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  } ]" + NEW_LINE +
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