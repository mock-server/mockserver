package org.mockserver.client.serialization.deserializers.body;

import com.google.common.base.Charsets;
import com.google.common.net.MediaType;
import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.*;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.NottableString.not;

public class BodyDTODeserializerTest {

    @Test
    public void shouldParseJSONWithInvalidBody() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithMissingTypeFromBody() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"value\" : \"some_value\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithMissingValueFromBody() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"STRING\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithWrongFieldInBody() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"STRING\"," + System.getProperty("line.separator") +
                "            \"wrong_name\" : \"some_value\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithWrongValueFieldTypeInStringBody() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"STRING\"," + System.getProperty("line.separator") +
                "            \"value\" : 1" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithWrongValueFieldTypeInBinaryBody() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"BINARY\"," + System.getProperty("line.separator") +
                "            \"value\" : 1" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new BinaryBodyDTO(new BinaryBody(new byte[0])))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithWrongValueFieldTypeInParameterBody() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "            \"value\" : 1" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new ParameterBodyDTO(new ParameterBody()))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithWrongTypeFieldTypeInBody() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : 1," + System.getProperty("line.separator") +
                "            \"value\" : \"some_value\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithEmptyContentType() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"contentType\" : \"\"," + System.getProperty("line.separator") +
                "            \"string\" : \"some_value\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("some_value")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithInvalidContentType() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"contentType\" : \"invalid_value\"," + System.getProperty("line.separator") +
                "            \"string\" : \"some_value\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("some_value")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithEmptyCharset() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"charset\" : \"\"," + System.getProperty("line.separator") +
                "            \"string\" : \"some_value\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("some_value")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithInvalidCharset() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"charset\" : \"invalid_value\"," + System.getProperty("line.separator") +
                "            \"string\" : \"some_value\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("some_value")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithParametersBody() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "            \"parameters\" : [ {" + System.getProperty("line.separator") +
                "                    \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"parameterOneValueOne\", \"parameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                "                }, {" + System.getProperty("line.separator") +
                "                    \"name\" : \"parameterTwoName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"parameterTwoValue\" ]" + System.getProperty("line.separator") +
                "            } ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new ParameterBodyDTO(new ParameterBody(
                                        new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                                        new Parameter("parameterTwoName", "parameterTwoValue")
                                )))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithParametersBodyWithNot() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"not\" : true," + System.getProperty("line.separator") +
                "            \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "            \"parameters\" : [ {" + System.getProperty("line.separator") +
                "                    \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"parameterOneValueOne\", \"parameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                "                }, {" + System.getProperty("line.separator") +
                "                    \"name\" : \"parameterTwoName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"parameterTwoValue\" ]" + System.getProperty("line.separator") +
                "            } ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new ParameterBodyDTO(new ParameterBody(
                                        new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                                        new Parameter("parameterTwoName", "parameterTwoValue")
                                ), true))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithParametersBodyWithNotParameter() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "      \"parameters\" : [ {" + System.getProperty("line.separator") +
                "        \"name\" : {" + System.getProperty("line.separator") +
                "          \"not\" : true," + System.getProperty("line.separator") +
                "          \"value\" : \"parameterOneName\"" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"values\" : [ {" + System.getProperty("line.separator") +
                "          \"not\" : true," + System.getProperty("line.separator") +
                "          \"value\" : \"parameterOneValueOne\"" + System.getProperty("line.separator") +
                "        }, {" + System.getProperty("line.separator") +
                "          \"not\" : true," + System.getProperty("line.separator") +
                "          \"value\" : \"parameterOneValueTwo\"" + System.getProperty("line.separator") +
                "        } ]" + System.getProperty("line.separator") +
                "      }, {" + System.getProperty("line.separator") +
                "        \"name\" : \"parameterTwoName\"," + System.getProperty("line.separator") +
                "        \"values\" : [ \"parameterTwoValue\" ]" + System.getProperty("line.separator") +
                "      } ]" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new ParameterBodyDTO(
                                        new ParameterBody(
                                                new Parameter(not("parameterOneName"), not("parameterOneValueOne"), not("parameterOneValueTwo")),
                                                new Parameter("parameterTwoName", "parameterTwoValue")
                                        )
                                ))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithParametersBodyWithNotParameterWithExclamationMark() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "            \"parameters\" : [ {" + System.getProperty("line.separator") +
                "                    \"name\" : \"!parameterOneName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"!parameterOneValueOne\", \"!parameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                "                }, {" + System.getProperty("line.separator") +
                "                    \"name\" : \"parameterTwoName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"parameterTwoValue\" ]" + System.getProperty("line.separator") +
                "            } ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new ParameterBodyDTO(
                                        new ParameterBody(
                                                new Parameter(not("parameterOneName"), not("parameterOneValueOne"), not("parameterOneValueTwo")),
                                                new Parameter("parameterTwoName", "parameterTwoValue")
                                        )
                                ))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithExactStringBodyAsString() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : \"some_value\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("some_value")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithExactStringBodyWithContentType() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"contentType\" : \"text/plain; charset=utf-8\"," + System.getProperty("line.separator") +
                "            \"string\" : \"some_value\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("some_value", MediaType.PLAIN_TEXT_UTF_8)))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithExactStringBodyWithCharset() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"charset\" : \"utf-8\"," + System.getProperty("line.separator") +
                "            \"string\" : \"some_value\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("some_value", MediaType.PLAIN_TEXT_UTF_8)))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithExactStringBodyWithoutType() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"string\" : \"some_value\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("some_value")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithExactStringBodyUsingStringPropertyWithNot() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"not\" : true," + System.getProperty("line.separator") +
                "            \"type\" : \"STRING\"," + System.getProperty("line.separator") +
                "            \"string\" : \"some_value\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("some_value"), true))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithExactStringBodyUsingValueProperty() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"STRING\"," + System.getProperty("line.separator") +
                "            \"value\" : \"some_value\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("some_value")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithExactStringBodyUsingStringProperty() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"STRING\"," + System.getProperty("line.separator") +
                "            \"string\" : \"some_value\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new StringBodyDTO(new StringBody("some_value")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithRegexBodyWithoutType() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"regex\" : \"some[a-zA-Z]*\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new RegexBodyDTO(new RegexBody("some[a-zA-Z]*")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithRegexBodyWithNot() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"not\" : true," + System.getProperty("line.separator") +
                "            \"regex\" : \"some[a-zA-Z]*\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new RegexBodyDTO(new RegexBody("some[a-zA-Z]*"), true))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithRegexBodyUsingRegexProperty() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"REGEX\"," + System.getProperty("line.separator") +
                "            \"regex\" : \"some[a-zA-Z]*\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new RegexBodyDTO(new RegexBody("some[a-zA-Z]*")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithRegexBodyUsingValueProperty() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"REGEX\"," + System.getProperty("line.separator") +
                "            \"value\" : \"some[a-zA-Z]*\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new RegexBodyDTO(new RegexBody("some[a-zA-Z]*")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithJsonBodyWithoutType() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"json\" : \"{'employees':[{'firstName':'John', 'lastName':'Doe'}]}\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithJsonBodyWithNot() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"not\" : true," + System.getProperty("line.separator") +
                "            \"json\" : \"{'employees':[{'firstName':'John', 'lastName':'Doe'}]}\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}"), true))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithJsonBodyUsingJsonProperty() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"JSON\"," + System.getProperty("line.separator") +
                "            \"json\" : \"{'employees':[{'firstName':'John', 'lastName':'Doe'}]}\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithJsonBodyWithMatchTypeAndContentType() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"JSON\"," + System.getProperty("line.separator") +
                "            \"matchType\" : \"STRICT\"," + System.getProperty("line.separator") +
                "            \"contentType\" : \"application/json; charset=utf-8\"," + System.getProperty("line.separator") +
                "            \"json\" : \"{'employees':[{'firstName':'John', 'lastName':'Doe'}]}\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}", MediaType.JSON_UTF_8, MatchType.STRICT)))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithJsonBodyWithContentType() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"JSON\"," + System.getProperty("line.separator") +
                "            \"charset\" : \"" + Charsets.ISO_8859_1 + "\"," + System.getProperty("line.separator") +
                "            \"json\" : \"{'employees':[{'firstName':'John', 'lastName':'Doe'}]}\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}", MediaType.JSON_UTF_8.withCharset(Charsets.ISO_8859_1), MatchType.ONLY_MATCHING_FIELDS)))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithJsonBodyUsingValueProperty() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"JSON\"," + System.getProperty("line.separator") +
                "            \"value\" : \"{'employees':[{'firstName':'John', 'lastName':'Doe'}]}\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithJsonSchemaBodyWithNot() throws IOException {
        // given
        String jsonSchema = "{" + System.getProperty("line.separator") +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + System.getProperty("line.separator") +
                "    \"title\": \"Product\"," + System.getProperty("line.separator") +
                "    \"description\": \"A product from Acme's catalog\"," + System.getProperty("line.separator") +
                "    \"type\": \"object\"," + System.getProperty("line.separator") +
                "    \"properties\": {" + System.getProperty("line.separator") +
                "        \"id\": {" + System.getProperty("line.separator") +
                "            \"description\": \"The unique identifier for a product\"," + System.getProperty("line.separator") +
                "            \"type\": \"integer\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"required\": [\"id\"]" + System.getProperty("line.separator") +
                "}";
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"not\" : true," + System.getProperty("line.separator") +
                "            \"jsonSchema\" : \"" + StringEscapeUtils.escapeJava(jsonSchema) + "\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new JsonSchemaBodyDTO(new JsonSchemaBody(jsonSchema), true))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithJsonSchemaBodyWithoutType() throws IOException {
        // given
        String jsonSchema = "{" + System.getProperty("line.separator") +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + System.getProperty("line.separator") +
                "    \"title\": \"Product\"," + System.getProperty("line.separator") +
                "    \"description\": \"A product from Acme's catalog\"," + System.getProperty("line.separator") +
                "    \"type\": \"object\"," + System.getProperty("line.separator") +
                "    \"properties\": {" + System.getProperty("line.separator") +
                "        \"id\": {" + System.getProperty("line.separator") +
                "            \"description\": \"The unique identifier for a product\"," + System.getProperty("line.separator") +
                "            \"type\": \"integer\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"required\": [\"id\"]" + System.getProperty("line.separator") +
                "}";
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"jsonSchema\" : \"" + StringEscapeUtils.escapeJava(jsonSchema) + "\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new JsonSchemaBodyDTO(new JsonSchemaBody(jsonSchema)))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithJsonSchemaBodyUsingJsonProperty() throws IOException {
        // given
        String jsonSchema = "{" + System.getProperty("line.separator") +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + System.getProperty("line.separator") +
                "    \"title\": \"Product\"," + System.getProperty("line.separator") +
                "    \"description\": \"A product from Acme's catalog\"," + System.getProperty("line.separator") +
                "    \"type\": \"object\"," + System.getProperty("line.separator") +
                "    \"properties\": {" + System.getProperty("line.separator") +
                "        \"id\": {" + System.getProperty("line.separator") +
                "            \"description\": \"The unique identifier for a product\"," + System.getProperty("line.separator") +
                "            \"type\": \"integer\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"required\": [\"id\"]" + System.getProperty("line.separator") +
                "}";
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"JSON_SCHEMA\"," + System.getProperty("line.separator") +
                "            \"jsonSchema\" : \"" + StringEscapeUtils.escapeJava(jsonSchema) + "\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new JsonSchemaBodyDTO(new JsonSchemaBody(jsonSchema)))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithJsonSchemaBodyUsingValueProperty() throws IOException {
        // given
        String jsonSchema = "{" + System.getProperty("line.separator") +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + System.getProperty("line.separator") +
                "    \"title\": \"Product\"," + System.getProperty("line.separator") +
                "    \"description\": \"A product from Acme's catalog\"," + System.getProperty("line.separator") +
                "    \"type\": \"object\"," + System.getProperty("line.separator") +
                "    \"properties\": {" + System.getProperty("line.separator") +
                "        \"id\": {" + System.getProperty("line.separator") +
                "            \"description\": \"The unique identifier for a product\"," + System.getProperty("line.separator") +
                "            \"type\": \"integer\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"required\": [\"id\"]" + System.getProperty("line.separator") +
                "}";
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"JSON_SCHEMA\"," + System.getProperty("line.separator") +
                "            \"value\" : \"" + StringEscapeUtils.escapeJava(jsonSchema) + "\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new JsonSchemaBodyDTO(new JsonSchemaBody(jsonSchema)))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithXPathBodyWithoutType() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"xpath\" : \"\\\\some\\\\xpath\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new XPathBodyDTO(new XPathBody("\\some\\xpath")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithXPathBodyWithNot() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"not\" : true," + System.getProperty("line.separator") +
                "            \"xpath\" : \"\\\\some\\\\xpath\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new XPathBodyDTO(new XPathBody("\\some\\xpath"), true))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithXPathBodyUsingXpathProperty() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"XPATH\"," + System.getProperty("line.separator") +
                "            \"xpath\" : \"\\\\some\\\\xpath\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new XPathBodyDTO(new XPathBody("\\some\\xpath")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithXPathBodyUsingValueProperty() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"XPATH\"," + System.getProperty("line.separator") +
                "            \"value\" : \"\\\\some\\\\xpath\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new XPathBodyDTO(new XPathBody("\\some\\xpath")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithXmlBodyWithoutType() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"xml\" : \"<some><xml></xml></some>\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithXmlBodyWithNot() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"not\" : true," + System.getProperty("line.separator") +
                "            \"xml\" : \"<some><xml></xml></some>\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>"), true))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithXmlBodyWithContentType() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"contentType\" : \"text/xml; charset=utf-8\"," + System.getProperty("line.separator") +
                "            \"xml\" : \"<some><xml></xml></some>\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>", MediaType.XML_UTF_8)))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithXmlBodyWithCharset() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"charset\" : \"" + Charsets.US_ASCII + "\"," + System.getProperty("line.separator") +
                "            \"xml\" : \"<some><xml></xml></some>\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>", MediaType.APPLICATION_XML_UTF_8.withCharset(Charsets.US_ASCII))))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithXmlBodyUsingXpathProperty() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"XPATH\"," + System.getProperty("line.separator") +
                "            \"xml\" : \"<some><xml></xml></some>\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithXmlBodyUsingValueProperty() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"XML\"," + System.getProperty("line.separator") +
                "            \"value\" : \"<some><xml></xml></some>\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>")))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithBinaryBodyWithoutType() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"bytes\" : \"" + DatatypeConverter.printBase64Binary("some_value".getBytes()) + "\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new BinaryBodyDTO(new BinaryBody("some_value".getBytes())))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithBinaryBodyWithNot() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"not\" : true," + System.getProperty("line.separator") +
                "            \"bytes\" : \"" + DatatypeConverter.printBase64Binary("some_value".getBytes()) + "\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new BinaryBodyDTO(new BinaryBody("some_value".getBytes()), true))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithBinaryBodyWithContentType() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"contentType\" : \"" + MediaType.ANY_VIDEO_TYPE + "\"," + System.getProperty("line.separator") +
                "            \"bytes\" : \"" + DatatypeConverter.printBase64Binary("some_value".getBytes()) + "\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        ExpectationDTO expected = new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new BinaryBodyDTO(new BinaryBody("some_value".getBytes(), MediaType.ANY_VIDEO_TYPE)))
                );
        assertEquals(expected, expectationDTO);
    }

    @Test
    public void shouldParseJSONWithBinaryBodyUsingBytesProperty() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"BINARY\"," + System.getProperty("line.separator") +
                "            \"bytes\" : \"" + DatatypeConverter.printBase64Binary("some_value".getBytes()) + "\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new BinaryBodyDTO(new BinaryBody("some_value".getBytes())))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithBinaryBodyUsingValueProperty() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"BINARY\"," + System.getProperty("line.separator") +
                "            \"value\" : \"" + DatatypeConverter.printBase64Binary("some_value".getBytes()) + "\"" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new BinaryBodyDTO(new BinaryBody("some_value".getBytes())))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithParameterBodyWithoutType() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "            \"parameters\" : [ {" + System.getProperty("line.separator") +
                "                    \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"parameterOneValueOne\", \"parameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                "                }, {" + System.getProperty("line.separator") +
                "                    \"name\" : \"parameterTwoName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"parameterTwoValue\" ]" + System.getProperty("line.separator") +
                "            } ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new ParameterBodyDTO(new ParameterBody(
                                        new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                                        new Parameter("parameterTwoName", "parameterTwoValue")
                                )))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithParameterBodyUsingParametersProperty() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "            \"parameters\" : [ {" + System.getProperty("line.separator") +
                "                    \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"parameterOneValueOne\", \"parameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                "                }, {" + System.getProperty("line.separator") +
                "                    \"name\" : \"parameterTwoName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"parameterTwoValue\" ]" + System.getProperty("line.separator") +
                "            } ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new ParameterBodyDTO(new ParameterBody(
                                        new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                                        new Parameter("parameterTwoName", "parameterTwoValue")
                                )))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithParameterBodyUsingValueProperty() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "            \"value\" : [ {" + System.getProperty("line.separator") +
                "                    \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"parameterOneValueOne\", \"parameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                "                }, {" + System.getProperty("line.separator") +
                "                    \"name\" : \"parameterTwoName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"parameterTwoValue\" ]" + System.getProperty("line.separator") +
                "            } ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new ParameterBodyDTO(new ParameterBody(
                                        new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                                        new Parameter("parameterTwoName", "parameterTwoValue")
                                )))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithInvalidArrayParameterBody() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"body\" : {" + System.getProperty("line.separator") +
                "            \"type\" : \"PARAMETERS\"," + System.getProperty("line.separator") +
                "            \"parameters\" : {" + System.getProperty("line.separator") +
                "                    \"name\" : \"parameterOneName\"," + System.getProperty("line.separator") +
                "                    \"values\" : [ \"parameterOneValueOne\", \"parameterOneValueTwo\" ]" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setBody(new ParameterBodyDTO(new ParameterBody()))
                ), expectationDTO);
    }

}