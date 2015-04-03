package org.mockserver.client.serialization.deserializers;

import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.*;
import org.mockserver.model.*;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;

import static org.junit.Assert.*;

public class BodyDTODeserializerTest {

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