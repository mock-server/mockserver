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
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.StringBody.exact;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class BodyWithContentTypeDTODeserializerTest {

    @Test
    public void shouldParseJsonBodyWithInvalidType() throws IOException {
        // given
        String json = ("{\"httpResponse\":{\"body\":{\"type\":\"info\",\"body\":[{\"info\":{\"address\":\"192.168.0.0\",\"os\":\"Windows\",\"name\":\"HOMEPC\",\"version\":\"XP SP3\",\"key\":\"WINXPSP3H\"}}],\"timestamp\":\"2020-02-19T08:09:32.802\"}}}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat( expectationDTO, is(new ExpectationDTO()
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
            )));
    }

    @Test
    public void shouldKeepSubStringOnAStringResponseBody() throws IOException {
        // given -- a response matcher (e.g. in a verification) asking for a substring match
        String json = "{\"httpResponse\":{\"body\":{\"type\":\"STRING\",\"string\":\"shipped\",\"subString\":true}}}";

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        StringBody body = (StringBody) expectationDTO.getHttpResponse().buildObject().getBody();
        assertThat(body.getValue(), is("shipped"));
        assertThat(body.isSubString(), is(true));
    }

    @Test
    public void shouldDefaultSubStringToFalseOnAStringResponseBody() throws IOException {
        // given
        String json = "{\"httpResponse\":{\"body\":{\"type\":\"STRING\",\"string\":\"shipped\"}}}";

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(((StringBody) expectationDTO.getHttpResponse().buildObject().getBody()).isSubString(), is(false));
    }

    @Test
    public void shouldParseFileBodyWithTemplateType() throws IOException {
        // given
        String json = ("{\"httpResponse\":{\"body\":{\"type\":\"FILE\",\"filePath\":\"some/path.json\",\"templateType\":\"MUSTACHE\",\"contentType\":\"application/json\"}}}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO().setBody(new FileBodyDTO(new FileBody("some/path.json", MediaType.parse("application/json"), HttpTemplate.TemplateType.MUSTACHE)))
            )));
    }

    @Test
    public void shouldParseFileBodyWithTemplatedFilePath() throws IOException {
        // given
        String json = ("{\"httpResponse\":{\"body\":{\"type\":\"FILE\",\"filePath\":\"files/{{ request.pathParameters.id.0 }}.json\",\"templateType\":\"MUSTACHE\",\"templatedFilePath\":true}}}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO().setBody(new FileBodyDTO(new FileBody("files/{{ request.pathParameters.id.0 }}.json", null, HttpTemplate.TemplateType.MUSTACHE).withTemplatedFilePath(true)))
            )));
        assertThat(((FileBodyDTO) expectationDTO.getHttpResponse().getBody()).buildObject().getTemplatedFilePath(), is(true));
    }

    @Test
    public void shouldParseFileBodyWithoutTemplateType() throws IOException {
        // given
        String json = ("{\"httpResponse\":{\"body\":{\"type\":\"FILE\",\"filePath\":\"some/path.json\"}}}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertThat(expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO().setBody(new FileBodyDTO(new FileBody("some/path.json")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("1")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new BinaryBodyDTO(new BinaryBody(new byte[0])))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO().setBody(BodyWithContentTypeDTO.createWithContentTypeDTO(exact("some_value")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(json("some_value")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value", MediaType.PLAIN_TEXT_UTF_8)))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value", MediaType.PLAIN_TEXT_UTF_8.withCharset(StandardCharsets.ISO_8859_1))))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value"), true))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO((StringBody) new StringBody("some_value").withOptional(true)))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new StringBodyDTO(new StringBody("some_value")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}"), true))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO((JsonBody) new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}").withOptional(true)))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{" + NEW_LINE +
                        "  \"employees\" : [ {" + NEW_LINE +
                        "    \"firstName\" : \"John\"," + NEW_LINE +
                        "    \"lastName\" : \"Doe\"" + NEW_LINE +
                        "  } ]" + NEW_LINE +
                        "}")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("[ {" + NEW_LINE +
                        "  \"firstName\" : \"John\"," + NEW_LINE +
                        "  \"lastName\" : \"Doe\"" + NEW_LINE +
                        "} ]")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{" + NEW_LINE +
                        "  \"emptyArray\" : \"[]\"" + NEW_LINE +
                        "}")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("[ ]")))
            )));

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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{ }")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{\"context\":[{\"source\":\"DECISION_REQUEST\"},{\"source\":\"DECISION_REQUEST\"},{\"source\":\"DECISION_REQUEST\"}]}")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
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
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
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
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}", null, MediaType.JSON_UTF_8, MatchType.ONLY_MATCHING_FIELDS)))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new JsonBodyDTO(new JsonBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}", null, MediaType.JSON_UTF_8.withCharset(StandardCharsets.ISO_8859_1), MatchType.ONLY_MATCHING_FIELDS)))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>"), true))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new XmlBodyDTO((XmlBody) new XmlBody("<some><xml></xml></some>").withOptional(true)))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>", MediaType.XML_UTF_8)))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>", MediaType.APPLICATION_XML_UTF_8.withCharset(StandardCharsets.US_ASCII))))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new XmlBodyDTO(new XmlBody("<some><xml></xml></some>")))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new BinaryBodyDTO(new BinaryBody("some_value".getBytes(UTF_8))))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new BinaryBodyDTO(new BinaryBody("some_value".getBytes(UTF_8)), true))
            )));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new BinaryBodyDTO((BinaryBody) new BinaryBody("some_value".getBytes(UTF_8)).withOptional(true)))
            )));
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
        assertThat( expectationDTO, is(expected));
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
        assertThat( expectationDTO, is(new ExpectationDTO()
            .setHttpResponse(
                new HttpResponseDTO()
                    .setBody(new BinaryBodyDTO(new BinaryBody("some_value".getBytes(UTF_8))))
            )));
    }

}
