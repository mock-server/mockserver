package org.mockserver.client.serialization;

import org.junit.Test;
import org.mockserver.client.serialization.model.*;
import org.mockserver.model.*;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public class HttpRequestSerializerIntegrationTest {


    @Test
    public void shouldIgnoreExtraFields() throws IOException {
        // given
        String requestBytes = ("{\n" +
                "    \"path\": \"somePath\",\n" +
                "    \"extra_field\": \"extra_value\"\n" +
                "}");

        // when
        HttpRequest expectation = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setPath("somePath")
                .buildObject(), expectation);
    }

    @Test
    public void shouldDeserializeCompleteObject() throws IOException {
        // given
        String requestBytes = ("{\n" +
                "  \"method\" : \"someMethod\",\n" +
                "  \"url\" : \"http://www.example.com\",\n" +
                "  \"path\" : \"somePath\",\n" +
                "  \"queryStringParameters\" : [ {\n" +
                "    \"name\" : \"queryParameterName\",\n" +
                "    \"values\" : [ \"queryParameterValue\" ]\n" +
                "  } ],\n" +
                "  \"body\" : {\n" +
                "    \"type\" : \"EXACT\",\n" +
                "    \"value\" : \"somebody\"\n" +
                "  },\n" +
                "  \"cookies\" : [ {\n" +
                "    \"name\" : \"someCookieName\",\n" +
                "    \"values\" : [ \"someCookieValue\" ]\n" +
                "  } ],\n" +
                "  \"headers\" : [ {\n" +
                "    \"name\" : \"someHeaderName\",\n" +
                "    \"values\" : [ \"someHeaderValue\" ]\n" +
                "  } ]\n" +
                "}");

        // when
        HttpRequest expectation = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setMethod("someMethod")
                .setURL("http://www.example.com")
                .setPath("somePath")
                .setQueryStringParameters(Arrays.<ParameterDTO>asList((ParameterDTO) new ParameterDTO(new Parameter("queryParameterName", Arrays.asList("queryParameterValue")))))
                .setBody(BodyDTO.createDTO(new StringBody("somebody", Body.Type.EXACT)))
                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
                .buildObject(), expectation);
    }

    /*
    expected: org.mockserver.model.HttpRequest<HttpRequest[method=someMethod,url=http://www.example.com,path=somePath,queryStringParameters={queryParameterName=Parameter[name=queryParameterName,values=[queryParameterValue]]},body=somebody,headers=[Header[name=someHeaderName,values=[someHeaderValue]]],cookies=[Cookie[name=someCookieName,values=[someCookieValue]]]]>
     but was: org.mockserver.model.HttpRequest<HttpRequest[method=someMethod,url=http://www.example.com,path=somePath,queryStringParameters={queryParameterName=Parameter[name=queryParameterName,values=[queryParameterValue]]},body=somebody,headers=[Header[name=someHeaderName,values=[someHeaderValue]]],cookies=[Cookie[name=someCookieName,values=[someCookieValue]]]]>
     */

    @Test
    public void shouldDeserializePartialObject() throws IOException {
        // given
        String requestBytes = ("{\n" +
                "    \"path\": \"somePath\"\n" +
                "}");

        // when
        HttpRequest expectation = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setPath("somePath")
                .buildObject(), expectation);
    }

    @Test
    public void shouldSerializeCompleteObject() throws IOException {
        // when
        String jsonExpectation = new HttpRequestSerializer().serialize(
                new HttpRequestDTO()
                        .setMethod("someMethod")
                        .setURL("http://www.example.com")
                        .setPath("somePath")
                        .setQueryStringParameters(Arrays.<ParameterDTO>asList((ParameterDTO) new ParameterDTO(new Parameter("queryParameterName", Arrays.asList("queryParameterValue")))))
                        .setBody(BodyDTO.createDTO(new StringBody("somebody", Body.Type.EXACT)))
                        .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                        .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", Arrays.asList("someCookieValue")))))
                        .buildObject()
        );

        // then
        assertEquals("{\n" +
                "  \"method\" : \"someMethod\",\n" +
                "  \"url\" : \"http://www.example.com\",\n" +
                "  \"path\" : \"somePath\",\n" +
                "  \"queryStringParameters\" : [ {\n" +
                "    \"name\" : \"queryParameterName\",\n" +
                "    \"values\" : [ \"queryParameterValue\" ]\n" +
                "  } ],\n" +
                "  \"body\" : {\n" +
                "    \"type\" : \"EXACT\",\n" +
                "    \"value\" : \"somebody\"\n" +
                "  },\n" +
                "  \"cookies\" : [ {\n" +
                "    \"name\" : \"someCookieName\",\n" +
                "    \"values\" : [ \"someCookieValue\" ]\n" +
                "  } ],\n" +
                "  \"headers\" : [ {\n" +
                "    \"name\" : \"someHeaderName\",\n" +
                "    \"values\" : [ \"someHeaderValue\" ]\n" +
                "  } ]\n" +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialRequestAndResponse() throws IOException {
        // when
        String jsonExpectation = new HttpRequestSerializer().serialize(new HttpRequestDTO()
                .setPath("somePath")
                .buildObject()
        );

        // then
        assertEquals("{\n" +
                "  \"path\" : \"somePath\"\n" +
                "}", jsonExpectation);
    }

    @Test
    public void shouldSerializePartialExpectation() throws IOException {
        // when
        String jsonExpectation = new HttpRequestSerializer().serialize(new HttpRequestDTO()
                .setPath("somePath")
                .buildObject()
        );

        // then
        assertEquals("{\n" +
                "  \"path\" : \"somePath\"\n" +
                "}", jsonExpectation);
    }
}
