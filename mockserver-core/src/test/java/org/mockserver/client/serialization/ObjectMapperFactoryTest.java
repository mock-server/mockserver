package org.mockserver.client.serialization;

import org.junit.Test;
import org.mockserver.client.serialization.model.*;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class ObjectMapperFactoryTest {


    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
        // given
        String json = ("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"url\" : \"http://www.example.com\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"STRING\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someBody\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"statusCode\" : 304," + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"delay\" : {" + System.getProperty("line.separator") +
                "      \"timeUnit\" : \"MICROSECONDS\"," + System.getProperty("line.separator") +
                "      \"value\" : 1" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}");

        // when
        ExpectationDTO expectationDTO = ObjectMapperFactory.createObjectMapper().readValue(json, ExpectationDTO.class);

        // then
        assertEquals(new ExpectationDTO()
                .setHttpRequest(
                        new HttpRequestDTO()
                                .setMethod("someMethod")
                                .setURL("http://www.example.com")
                                .setPath("somePath")
                                .setQueryStringParameters(Arrays.asList(
                                        new ParameterDTO(new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two")),
                                        new ParameterDTO(new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One"))
                                ))
                                .setBody(new StringBodyDTO(new StringBody("someBody", Body.Type.STRING)))
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
                )
                .setHttpResponse(
                        new HttpResponseDTO()
                                .setStatusCode(304)
                                .setBody(new StringBodyDTO(new StringBody("someBody", Body.Type.STRING)))
                                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
                                .setDelay(
                                        new DelayDTO()
                                                .setTimeUnit(TimeUnit.MICROSECONDS)
                                                .setValue(1)
                                )
                )
                .setTimes(new TimesDTO(Times.exactly(5))), expectationDTO);
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
    public void shouldParseJSONWithExactStringBody() throws IOException {
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
                                .setBody(new StringBodyDTO(new StringBody("some_value", Body.Type.STRING)))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithRegexStringBody() throws IOException {
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
                                .setBody(new StringBodyDTO(new StringBody("some[a-zA-Z]*", Body.Type.REGEX)))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithJsonStringBody() throws IOException {
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
                                .setBody(new StringBodyDTO(new StringBody("{'employees':[{'firstName':'John', 'lastName':'Doe'}]}", Body.Type.JSON)))
                ), expectationDTO);
    }


    @Test
    public void shouldParseJSONWithXPathStringBody() throws IOException {
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
                                .setBody(new StringBodyDTO(new StringBody("\\some\\xpath", Body.Type.XPATH)))
                ), expectationDTO);
    }

    @Test
    public void shouldParseJSONWithBinaryBody() throws IOException {
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
    public void shouldParseJSONWithParameterBody() throws IOException {
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
                ), expectationDTO);
    }
}
