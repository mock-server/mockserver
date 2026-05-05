package org.mockserver.serialization.deserializers.body;

import org.junit.Test;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.*;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.*;

import jakarta.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.UTF_8;
import static junit.framework.TestCase.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.StringBody.exact;

public class BodyWithContentTypeDTODeserializerTest {

    @Test
    public void shouldParseJsonBodyWithInvalidType() throws IOException {
        // given
        String json = ("{\"httpResponse\":{\"body\":{\"type\":\"info\",\"body\":[{\"info\":{\"address\":\"192.168.0.0\",\"os\":\"Windows\",\"name\":\"HOMEPC\",\"version\":\"XP SP3\",\"key\":\"WINXPSP3H\"}}],\"timestamp\":\"2020-02-19T08:09:32.802\"}}}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO().setBody(new JsonBodyDTO(json("{\n" +
                    "  \"type\" : \"info\",\n" +
                    "  \"body\" : [ {\n" +
                    "    \"info\" : {\n" +
                    "      \"address\" : \"192.168.0.0\",\n" +
                    "      \"os\" : \"Windows\",\n" +
                    "      \"name\" : \"HOMEPC\",\n" +
                    "      \"version\" : \"XP SP3\",\n" +
                    "      \"key\" : \"WINXPSP3H\"\n" +
                    "    }\n" +
                    "  } ],\n" +
                    "  \"timestamp\" : \"2020-02-19T08:09:32.802\"\n" +
                    "}")))
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
                new HttpResponseDTO().setBody(BodyWithContentTypeDTO.createWithContentTypeDTO(exact("some_value")))
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
    public void shouldParseJsonWithExactStringBodyUsingStringPropertyWithNot() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"not\" : true," + NEW_LINE +
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
                    .setBody(new StringBodyDTO(new StringBody("some_value"), true))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithExactStringBodyUsingStringPropertyWithOptional() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"optional\" : true," + NEW_LINE +
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
                    .setBody(new StringBodyDTO((StringBody) new StringBody("some_value").withOptional(true)))
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
    public void shouldParseJsonWithJsonBodyWithOptional() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"optional\" : true," + NEW_LINE +
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
                    .setBody(new JsonBodyDTO((JsonBody) new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}").withOptional(true)))
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
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"JSON\"," + NEW_LINE +
            "      \"json\" : {" + NEW_LINE +
            "        \"employees\" : [ {" + NEW_LINE +
            "          \"firstName\" : \"John\"," + NEW_LINE +
            "          \"lastName\" : \"Doe\"" + NEW_LINE +
            "        } ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{" + NEW_LINE +
                        "  \"employees\" : [ {" + NEW_LINE +
                        "    \"firstName\" : \"John\"," + NEW_LINE +
                        "    \"lastName\" : \"Doe\"" + NEW_LINE +
                        "  } ]" + NEW_LINE +
                        "}")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyAsArray() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"JSON\"," + NEW_LINE +
            "      \"json\" : [ {" + NEW_LINE +
            "        \"firstName\" : \"John\"," + NEW_LINE +
            "        \"lastName\" : \"Doe\"" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("[ {" + NEW_LINE +
                        "  \"firstName\" : \"John\"," + NEW_LINE +
                        "  \"lastName\" : \"Doe\"" + NEW_LINE +
                        "} ]")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyWithEmptyArray() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"JSON\"," + NEW_LINE +
            "      \"json\" : {" + NEW_LINE +
            "        \"emptyArray\" : \"[]\"" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{" + NEW_LINE +
                        "  \"emptyArray\" : \"[]\"" + NEW_LINE +
                        "}")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyWithSimpleEmptyArray() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : [ ]" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("[ ]")))
            ), expectationDTO);

    }

    @Test
    public void shouldParseJsonWithJsonBodyWithEmptyObject() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : { }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{ }")))
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
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"JSON\"," + NEW_LINE +
            "      \"json\" : {" + NEW_LINE +
            "        \"context\" : [ {" + NEW_LINE +
            "          \"source\" : \"DECISION_REQUEST\"" + NEW_LINE +
            "        }, {" + NEW_LINE +
            "          \"source\" : \"DECISION_REQUEST\"" + NEW_LINE +
            "        }, {" + NEW_LINE +
            "          \"source\" : \"DECISION_REQUEST\"" + NEW_LINE +
            "        } ]" + NEW_LINE +
            "      }" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{" + NEW_LINE +
                        "  \"context\" : [ {" + NEW_LINE +
                        "    \"source\" : \"DECISION_REQUEST\"" + NEW_LINE +
                        "  }, {" + NEW_LINE +
                        "    \"source\" : \"DECISION_REQUEST\"" + NEW_LINE +
                        "  }, {" + NEW_LINE +
                        "    \"source\" : \"DECISION_REQUEST\"" + NEW_LINE +
                        "  } ]" + NEW_LINE +
                        "}")))
            ), expectationDTO);
    }

    @Test
    public void shouldParseJsonWithJsonBodyAsObjectFieldAsArray() throws IOException {
        // given
        String json = "{" + NEW_LINE +
            "  \"httpResponse\" : {" + NEW_LINE +
            "    \"body\" : {" + NEW_LINE +
            "      \"type\" : \"JSON\"," + NEW_LINE +
            "      \"json\" : [ {" + NEW_LINE +
            "        \"context\" : [ {" + NEW_LINE +
            "          \"source\" : \"DECISION_REQUEST\"" + NEW_LINE +
            "        }, {" + NEW_LINE +
            "          \"source\" : \"DECISION_REQUEST\"" + NEW_LINE +
            "        }, {" + NEW_LINE +
            "          \"source\" : \"DECISION_REQUEST\"" + NEW_LINE +
            "        } ]" + NEW_LINE +
            "      } ]" + NEW_LINE +
            "    }" + NEW_LINE +
            "  }" + NEW_LINE +
            "}";

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("[ {" + NEW_LINE +
                        "  \"context\" : [ {" + NEW_LINE +
                        "    \"source\" : \"DECISION_REQUEST\"" + NEW_LINE +
                        "  }, {" + NEW_LINE +
                        "    \"source\" : \"DECISION_REQUEST\"" + NEW_LINE +
                        "  }, {" + NEW_LINE +
                        "    \"source\" : \"DECISION_REQUEST\"" + NEW_LINE +
                        "  } ]" + NEW_LINE +
                        "} ]")))
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
    public void shouldParseJsonWithXmlBodyWithOptional() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"optional\" : true," + NEW_LINE +
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
                    .setBody(new XmlBodyDTO((XmlBody) new XmlBody("<some><xml></xml></some>").withOptional(true)))
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
    public void shouldParseJsonWithBinaryBodyWithOptional() throws IOException {
        // given
        String json = ("{" + NEW_LINE +
            "    \"httpResponse\": {" + NEW_LINE +
            "        \"body\" : {" + NEW_LINE +
            "            \"optional\" : true," + NEW_LINE +
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
                    .setBody(new BinaryBodyDTO((BinaryBody) new BinaryBody("some_value".getBytes(UTF_8)).withOptional(true)))
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
