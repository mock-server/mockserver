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
    public void shouldSerializeArrayFormatParameterBodyDTO() throws IOException {
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
    public void shouldSerializeObjectFormatParameterBodyDTO() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
            "  \"parameters\" : {" + NEW_LINE +
            "    \"queryStringParameterOneName\" : [ \"queryStringParameterOneValueOne\", \"queryStringParameterOneValueTwo\" ]," + NEW_LINE +
            "    \"queryStringParameterTwoName\" : [ \"queryStringParameterTwoValue\" ]" + NEW_LINE +
            "  }" + NEW_LINE +
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
    public void shouldSerializeArrayFormatParameterBodyDTOWithNot() throws IOException {
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
    public void shouldSerializeObjectFormatParameterBodyDTOWithNot() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"not\" : true," + NEW_LINE +
            "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
            "  \"parameters\" : {" + NEW_LINE +
            "    \"queryStringParameterTwoName\" : [ \"queryStringParameterTwoValue\" ]," + NEW_LINE +
            "    \"queryStringParameterOneName\" : [ \"queryStringParameterOneValueOne\", \"queryStringParameterOneValueTwo\" ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        BodyDTO bodyDTO = ObjectMapperFactory.createObjectMapper().readValue(json, BodyDTO.class);

        // then
        assertThat(bodyDTO,
            Is.<BodyDTO>is(new ParameterBodyDTO(params(
                param("queryStringParameterTwoName", "queryStringParameterTwoValue"),
                param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo")
            ), true))
        );
    }

    @Test
    public void shouldSerializeArrayFormatParameterBodyDTOWithAllNottedParameterKeys() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
            "  \"parameters\" : [ {" + NEW_LINE +
            "    \"name\" : {" + NEW_LINE +
            "      \"not\" : true," + NEW_LINE +
            "      \"value\" : \"queryStringParameterOneName\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"values\" : [ {" + NEW_LINE +
            "      \"not\" : false," + NEW_LINE +
            "      \"value\" : \"queryStringParameterOneValueOne\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"not\" : false," + NEW_LINE +
            "      \"value\" : \"queryStringParameterOneValueTwo\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"name\" : {" + NEW_LINE +
            "      \"not\" : true," + NEW_LINE +
            "      \"value\" : \"queryStringParameterTwoName\"" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"values\" : [ {" + NEW_LINE +
            "      \"not\" : false," + NEW_LINE +
            "      \"value\" : \"queryStringParameterTwoValue\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  } ]" + NEW_LINE +
            "}");

        // when
        BodyDTO bodyDTO = ObjectMapperFactory.createObjectMapper().readValue(json, BodyDTO.class);

        // then
        assertThat(bodyDTO,
            Is.<BodyDTO>is(new ParameterBodyDTO(params(
                param(not("queryStringParameterOneName"), string("queryStringParameterOneValueOne"), string("queryStringParameterOneValueTwo")),
                param(not("queryStringParameterTwoName"), string("queryStringParameterTwoValue"))
            )))
        );
    }

    @Test
    public void shouldSerializeObjectFormatParameterBodyDTOWithAllNottedParameterKeys() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
            "  \"parameters\" : {" + NEW_LINE +
            "    \"!queryStringParameterOneName\" : [ \"queryStringParameterOneValueOne\", \"queryStringParameterOneValueTwo\" ]," + NEW_LINE +
            "    \"!queryStringParameterTwoName\" : [ \"queryStringParameterTwoValue\" ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        BodyDTO bodyDTO = ObjectMapperFactory.createObjectMapper().readValue(json, BodyDTO.class);

        // then
        assertThat(bodyDTO,
            Is.<BodyDTO>is(new ParameterBodyDTO(params(
                param(not("queryStringParameterOneName"), string("queryStringParameterOneValueOne"), string("queryStringParameterOneValueTwo")),
                param(not("queryStringParameterTwoName"), string("queryStringParameterTwoValue"))
            )))
        );
    }

    @Test
    public void shouldSerializeArrayFormatParameterBodyDTOWithAllNottedParameterValues() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
            "  \"parameters\" : [ {" + NEW_LINE +
            "    \"name\" : \"queryStringParameterOneName\"," + NEW_LINE +
            "    \"values\" : [ {" + NEW_LINE +
            "      \"not\" : true," + NEW_LINE +
            "      \"value\" : \"queryStringParameterOneValueOne\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "      \"not\" : true," + NEW_LINE +
            "      \"value\" : \"queryStringParameterOneValueTwo\"" + NEW_LINE +
            "    } ]" + NEW_LINE +
            "  }, {" + NEW_LINE +
            "    \"name\" : {" + NEW_LINE +
            "      \"not\" : false," + NEW_LINE +
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
                param(string("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                param(string("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
            )))
        );
    }

    @Test
    public void shouldSerializeObjectFormatParameterBodyDTOWithAllNottedParameterValues() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
            "  \"parameters\" : {" + NEW_LINE +
            "    \"queryStringParameterOneName\" : [ \"!queryStringParameterOneValueOne\", \"!queryStringParameterOneValueTwo\" ]," + NEW_LINE +
            "    \"queryStringParameterTwoName\" : [ \"!queryStringParameterTwoValue\" ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        BodyDTO bodyDTO = ObjectMapperFactory.createObjectMapper().readValue(json, BodyDTO.class);

        // then
        assertThat(bodyDTO,
            Is.<BodyDTO>is(new ParameterBodyDTO(params(
                param(string("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                param(string("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
            )))
        );
    }

    @Test
    public void shouldSerializeArrayFormatParameterBodyDTOWithAllNottedParameterKeysAndValue() throws IOException {
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
    public void shouldSerializeObjectFormatParameterBodyDTOWithAllNottedParameterKeysAndValue() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
            "  \"parameters\" : {" + NEW_LINE +
            "    \"!queryStringParameterOneName\" : [ \"!queryStringParameterOneValueOne\", \"!queryStringParameterOneValueTwo\" ]," + NEW_LINE +
            "    \"!queryStringParameterTwoName\" : [ \"!queryStringParameterTwoValue\" ]," + NEW_LINE +
            "    \"!queryStringParameterThreeName\" : [ \"!queryStringParameterThreeValueOne\", \"!queryStringParameterThreeValueTwo\", \"!queryStringParameterThreeValueThree\" ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        BodyDTO bodyDTO = ObjectMapperFactory.createObjectMapper().readValue(json, BodyDTO.class);

        // then
        assertThat(bodyDTO,
            Is.<BodyDTO>is(new ParameterBodyDTO(params(
                param(not("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                param(not("queryStringParameterTwoName"), not("queryStringParameterTwoValue")),
                param(not("queryStringParameterThreeName"), not("queryStringParameterThreeValueOne"), not("queryStringParameterThreeValueTwo"), not("queryStringParameterThreeValueThree"))
            )))
        );
    }

    @Test
    public void shouldSerializeArrayFormatParameterBodyDTOWithAMixtureOfNottedAndStringParameterKeysAndValue() throws IOException {
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

    @Test
    public void shouldSerializeObjectFormatParameterBodyDTOWithAMixtureOfNottedAndStringParameterKeysAndValue() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
            "  \"parameters\" : {" + NEW_LINE +
            "    \"!queryStringParameterOneName\" : [ \"queryStringParameterOneValueOne\", \"!queryStringParameterOneValueTwo\" ]," + NEW_LINE +
            "    \"queryStringParameterTwoName\" : [ \"!queryStringParameterTwoValue\" ]" + NEW_LINE +
            "  }" + NEW_LINE +
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
