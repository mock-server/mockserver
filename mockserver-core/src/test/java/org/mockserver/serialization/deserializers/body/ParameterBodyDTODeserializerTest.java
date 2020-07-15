package org.mockserver.serialization.deserializers.body;

import org.hamcrest.core.Is;
import org.junit.Test;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.Parameters;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.BodyDTO;
import org.mockserver.serialization.model.ParameterBodyDTO;

import java.io.IOException;

import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;

public class ParameterBodyDTODeserializerTest {

    @Test
    public void shouldDeserializeArrayFormatParameterBodyDTO() throws IOException {
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
            Is.is(new ParameterBodyDTO(params(
                param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                param("queryStringParameterTwoName", "queryStringParameterTwoValue")
            )))
        );
    }

    @Test
    public void shouldDeserializeObjectFormatParameterBodyDTO() throws IOException {
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
            Is.is(new ParameterBodyDTO(params(
                param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                param("queryStringParameterTwoName", "queryStringParameterTwoValue")
            )))
        );
    }

    @Test
    public void shouldDeserializeArrayFormatParameterBodyDTOWithKeyMatchStyle() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
            "  \"parameters\" : {" + NEW_LINE +
            "    \"keyMatchStyle\" : \"MATCHING_KEY\"," + NEW_LINE +
            "    \"queryStringParameterOneName\" : [ \"queryStringParameterOneValueOne\", \"queryStringParameterOneValueTwo\" ]," + NEW_LINE +
            "    \"queryStringParameterTwoName\" : [ \"queryStringParameterTwoValue\" ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        BodyDTO bodyDTO = ObjectMapperFactory.createObjectMapper().readValue(json, BodyDTO.class);

        // then
        assertThat(bodyDTO,
            Is.is(new ParameterBodyDTO(params(new Parameters(
                param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                param("queryStringParameterTwoName", "queryStringParameterTwoValue")
            ).withKeyMatchStyle(KeyMatchStyle.MATCHING_KEY))))
        );
    }

    @Test
    public void shouldDeserializeArrayFormatParameterBodyDTOWithNot() throws IOException {
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
            Is.is(new ParameterBodyDTO(params(
                param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                param("queryStringParameterTwoName", "queryStringParameterTwoValue")
            ), true))
        );
    }

    @Test
    public void shouldDeserializeObjectFormatParameterBodyDTOWithNot() throws IOException {
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
            Is.is(new ParameterBodyDTO(params(
                param("queryStringParameterTwoName", "queryStringParameterTwoValue"),
                param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo")
            ), true))
        );
    }

    @Test
    public void shouldDeserializeArrayFormatParameterBodyDTOWithAllNottedParameterKeys() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
            "  \"parameters\" : {" + NEW_LINE +
            "    \"!queryStringParameterOneName\" : [ \"queryStringParameterOneValueOne\", \"queryStringParameterOneValueTwo\" ]," + NEW_LINE +
            "    \"!queryStringParameterTwoName\" : [ \"queryStringParameterTwoValue\" ]," + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        BodyDTO bodyDTO = ObjectMapperFactory.createObjectMapper().readValue(json, BodyDTO.class);

        // then
        assertThat(bodyDTO,
            Is.is(new ParameterBodyDTO(params(
                param(not("queryStringParameterOneName"), string("queryStringParameterOneValueOne"), string("queryStringParameterOneValueTwo")),
                param(not("queryStringParameterTwoName"), string("queryStringParameterTwoValue"))
            )))
        );
    }

    @Test
    public void shouldDeserializeObjectFormatParameterBodyDTOWithAllNottedParameterKeys() throws IOException {
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
            Is.is(new ParameterBodyDTO(params(
                param(not("queryStringParameterOneName"), string("queryStringParameterOneValueOne"), string("queryStringParameterOneValueTwo")),
                param(not("queryStringParameterTwoName"), string("queryStringParameterTwoValue"))
            )))
        );
    }

    @Test
    public void shouldDeserializeArrayFormatParameterBodyDTOWithAllNottedParameterValues() throws IOException {
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
            Is.is(new ParameterBodyDTO(params(
                param(string("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                param(string("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
            )))
        );
    }

    @Test
    public void shouldDeserializeObjectFormatParameterBodyDTOWithAllNottedParameterValues() throws IOException {
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
            Is.is(new ParameterBodyDTO(params(
                param(string("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                param(string("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
            )))
        );
    }

    @Test
    public void shouldDeserializeArrayFormatParameterBodyDTOWithAllNottedParameterKeysAndValue() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"type\" : \"PARAMETERS\"," + NEW_LINE +
            "  \"parameters\" : {" + NEW_LINE +
            "    \"!queryStringParameterOneName\" : [ \"!queryStringParameterOneValueOne\", \"!queryStringParameterOneValueTwo\" ]," + NEW_LINE +
            "    \"!queryStringParameterTwoName\" : [ \"!queryStringParameterTwoValue\" ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        BodyDTO bodyDTO = ObjectMapperFactory.createObjectMapper().readValue(json, BodyDTO.class);

        // then
        assertThat(bodyDTO,
            Is.is(new ParameterBodyDTO(params(
                param(not("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                param(not("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
            )))
        );
    }

    @Test
    public void shouldDeserializeObjectFormatParameterBodyDTOWithAllNottedParameterKeysAndValue() throws IOException {
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
            Is.is(new ParameterBodyDTO(params(
                param(not("queryStringParameterOneName"), not("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                param(not("queryStringParameterTwoName"), not("queryStringParameterTwoValue")),
                param(not("queryStringParameterThreeName"), not("queryStringParameterThreeValueOne"), not("queryStringParameterThreeValueTwo"), not("queryStringParameterThreeValueThree"))
            )))
        );
    }

    @Test
    public void shouldDeserializeArrayFormatParameterBodyDTOWithAMixtureOfNottedAndStringParameterKeysAndValue() throws IOException {
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
            Is.is(new ParameterBodyDTO(params(
                param(not("queryStringParameterOneName"), string("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                param(string("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
            )))
        );
    }

    @Test
    public void shouldDeserializeObjectFormatParameterBodyDTOWithAMixtureOfNottedAndStringParameterKeysAndValue() throws IOException {
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
            Is.is(new ParameterBodyDTO(params(
                param(not("queryStringParameterOneName"), string("queryStringParameterOneValueOne"), not("queryStringParameterOneValueTwo")),
                param(string("queryStringParameterTwoName"), not("queryStringParameterTwoValue"))
            )))
        );
    }

}
