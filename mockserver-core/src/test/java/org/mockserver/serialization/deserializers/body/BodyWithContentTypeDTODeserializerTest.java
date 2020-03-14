package org.mockserver.serialization.deserializers.body;

import org.junit.Test;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.*;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.*;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.StringBody.exact;

public class BodyWithContentTypeDTODeserializerTest {

    @Test
    public void shouldParseJsonWithInvalidBody() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithMissingValueFromBody() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"type\" : \"STRING\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithWrongFieldInBody() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"type\" : \"STRING\"," + NEW_LINE +
            "            \"wrong_name\" : \"some_value\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithWrongValueFieldTypeInStringBody() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"type\" : \"STRING\"," + NEW_LINE +
            "            \"string\" : 1" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("1")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithWrongValueFieldTypeInBinaryBody() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"type\" : \"BINARY\"," + NEW_LINE +
            "            \"binary\" : 1" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new BinaryBodyDTO(new BinaryBody(new byte[0])))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithWrongTypeFieldTypeInBody() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"type\" : 1," + NEW_LINE +
            "            \"string\" : \"some_value\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO().setBody(BodyWithContentTypeDTO.createDTO(exact("some_value")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithEmptyContentType() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"contentType\" : \"\"," + NEW_LINE +
            "            \"json\" : \"some_value\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(json("some_value")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithInvalidContentType() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"contentType\" : \"invalid_value\"," + NEW_LINE +
            "            \"string\" : \"some_value\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithEmptyCharset() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"charset\" : \"\"," + NEW_LINE +
            "            \"string\" : \"some_value\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithInvalidCharset() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"charset\" : \"invalid_value\"," + NEW_LINE +
            "            \"string\" : \"some_value\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithExactStringBodyAsString() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : \"some_value\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithExactStringBodyWithContentType() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"contentType\" : \"text/plain; charset=utf-8\"," + NEW_LINE +
            "            \"string\" : \"some_value\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value", MediaType.PLAIN_TEXT_UTF_8)))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithExactStringBodyWithCharset() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"charset\" : \"" + StandardCharsets.ISO_8859_1 + "\"," + NEW_LINE +
            "            \"string\" : \"some_value\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value", MediaType.PLAIN_TEXT_UTF_8.withCharset(StandardCharsets.ISO_8859_1))))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithExactStringBodyWithoutType() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"string\" : \"some_value\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithExactStringBodyUsingStringProperty() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"type\" : \"STRING\"," + NEW_LINE +
            "            \"string\" : \"some_value\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyWithoutType() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"json\" : \"{'employees':[{'firstName':'John', 'lastName':'Doe'}]}\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyWithNot() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"not\" : true," + NEW_LINE +
            "            \"json\" : \"{'employees':[{'firstName':'John', 'lastName':'Doe'}]}\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}"), true))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyUsingJsonProperty() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"type\" : \"JSON\"," + NEW_LINE +
            "            \"json\" : \"{'employees':[{'firstName':'John', 'lastName':'Doe'}]}\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyAsObject() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {\"employees\":[{\"firstName\":\"John\", \"lastName\":\"Doe\"}]}" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{\"employees\":[{\"firstName\":\"John\",\"lastName\":\"Doe\"}]}")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyAsArray() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : [{\"firstName\":\"John\", \"lastName\":\"Doe\"}]" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("[{\"firstName\":\"John\",\"lastName\":\"Doe\"}]")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyWithEmptyArray() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {\"emptyArray\":\"[]\"}" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{\"emptyArray\":\"[]\"}")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyAsObjectFieldAsString() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\": {" + NEW_LINE +
            "            \"type\": \"JSON\"," + NEW_LINE +
            "            \"json\": \"{\\\"context\\\":[{\\\"source\\\":\\\"DECISION_REQUEST\\\"},{\\\"source\\\":\\\"DECISION_REQUEST\\\"},{\\\"source\\\":\\\"DECISION_REQUEST\\\"}]}\"," + NEW_LINE +
            "            \"matchType\" : \"ONLY_MATCHING_FIELDS\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{\"context\":[{\"source\":\"DECISION_REQUEST\"},{\"source\":\"DECISION_REQUEST\"},{\"source\":\"DECISION_REQUEST\"}]}")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyAsObjectFieldAsObject() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\": {" + NEW_LINE +
            "            \"type\": \"JSON\"," + NEW_LINE +
            "            \"json\": {\"context\": [{\"source\": \"DECISION_REQUEST\"},{\"source\": \"DECISION_REQUEST\"},{\"source\": \"DECISION_REQUEST\"}]}," + NEW_LINE +
            "            \"matchType\" : \"ONLY_MATCHING_FIELDS\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{\"context\":[{\"source\":\"DECISION_REQUEST\"},{\"source\":\"DECISION_REQUEST\"},{\"source\":\"DECISION_REQUEST\"}]}")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyAsObjectFieldAsArray() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\": {" + NEW_LINE +
            "            \"type\": \"JSON\"," + NEW_LINE +
            "            \"json\": [{\"context\": [{\"source\": \"DECISION_REQUEST\"},{\"source\": \"DECISION_REQUEST\"},{\"source\": \"DECISION_REQUEST\"}]}]," + NEW_LINE +
            "            \"matchType\" : \"ONLY_MATCHING_FIELDS\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("[{\"context\":[{\"source\":\"DECISION_REQUEST\"},{\"source\":\"DECISION_REQUEST\"},{\"source\":\"DECISION_REQUEST\"}]}]")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyWithMatchTypeAndContentType() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"type\" : \"JSON\"," + NEW_LINE +
            "            \"contentType\" : \"application/json; charset=utf-8\"," + NEW_LINE +
            "            \"json\" : \"{'employees':[{'firstName':'John', 'lastName':'Doe'}]}\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}", null, MediaType.JSON_UTF_8, MatchType.ONLY_MATCHING_FIELDS)))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyWithContentType() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"type\" : \"JSON\"," + NEW_LINE +
            "            \"charset\" : \"" + StandardCharsets.ISO_8859_1 + "\"," + NEW_LINE +
            "            \"json\" : \"{'employees':[{'firstName':'John', 'lastName':'Doe'}]}\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}", null, MediaType.JSON_UTF_8.withCharset(StandardCharsets.ISO_8859_1), MatchType.ONLY_MATCHING_FIELDS)))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithXmlBodyWithoutType() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"xml\" : \"<some><xml></xml></some>\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithXmlBodyWithNot() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"not\" : true," + NEW_LINE +
            "            \"xml\" : \"<some><xml></xml></some>\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>"), true))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithXmlBodyWithContentType() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"contentType\" : \"text/xml; charset=utf-8\"," + NEW_LINE +
            "            \"xml\" : \"<some><xml></xml></some>\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>", MediaType.XML_UTF_8)))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithXmlBodyWithCharset() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"charset\" : \"" + StandardCharsets.US_ASCII + "\"," + NEW_LINE +
            "            \"xml\" : \"<some><xml></xml></some>\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>", MediaType.APPLICATION_XML_UTF_8.withCharset(StandardCharsets.US_ASCII))))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithXmlBodyUsingXpathProperty() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"type\" : \"XPATH\"," + NEW_LINE +
            "            \"xml\" : \"<some><xml></xml></some>\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithBinaryBodyWithoutType() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"base64Bytes\" : \"" + DatatypeConverter.printBase64Binary("some_value".getBytes(UTF_8)) + "\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new BinaryBodyDTO(new BinaryBody("some_value".getBytes(UTF_8))))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithBinaryBodyWithNot() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"not\" : true," + NEW_LINE +
            "            \"base64Bytes\" : \"" + DatatypeConverter.printBase64Binary("some_value".getBytes(UTF_8)) + "\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new BinaryBodyDTO(new BinaryBody("some_value".getBytes(UTF_8)), true))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithBinaryBodyWithContentType() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"contentType\" : \"" + MediaType.ANY_VIDEO_TYPE + "\"," + NEW_LINE +
            "            \"base64Bytes\" : \"" + DatatypeConverter.printBase64Binary("some_value".getBytes(UTF_8)) + "\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        ExpectationDTO expected = new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new BinaryBodyDTO(new BinaryBody("some_value".getBytes(UTF_8), MediaType.ANY_VIDEO_TYPE)))
            );
        assertEquals(expected, expectationDTO);
    }

    @Test
    public void shouldParseJsonWithBinaryBodyUsingBytesProperty() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"type\" : \"BINARY\"," + NEW_LINE +
            "            \"base64Bytes\" : \"" + DatatypeConverter.printBase64Binary("some_value".getBytes(UTF_8)) + "\"" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new BinaryBodyDTO(new BinaryBody("some_value".getBytes(UTF_8))))
            ), expectationDTO);
    }

}
