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
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "    \"path\": \"somePath\"," + System.getProperty("line.separator") +
                "    \"extra_field\": \"extra_value\"" + System.getProperty("line.separator") +
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
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "  \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "  \"url\" : \"http://www.example.com\"," + System.getProperty("line.separator") +
                "  \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "  \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"queryParameterName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"queryParameterValue\" ]" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"body\" : {" + System.getProperty("line.separator") +
                "    \"type\" : \"EXACT\"," + System.getProperty("line.separator") +
                "    \"value\" : \"somebody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"headers\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "  } ]" + System.getProperty("line.separator") +
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

    @Test
    public void shouldDeserializePartialObject() throws IOException {
        // given
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "    \"path\": \"somePath\"" + System.getProperty("line.separator") +
                "}");

        // when
        HttpRequest expectation = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setPath("somePath")
                .buildObject(), expectation);
    }

    @Test
    public void shouldDeserializeAsHttpRequestField() throws IOException {
        // given
        String requestBytes = ("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"path\": \"somePath\"," + System.getProperty("line.separator") +
                "        \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "            \"name\" : \"queryParameterName\"," + System.getProperty("line.separator") +
                "            \"values\" : [ \"queryParameterValue\" ]" + System.getProperty("line.separator") +
                "        } ]" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // when
        HttpRequest expectation = new HttpRequestSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setPath("somePath")
                .setQueryStringParameters(Arrays.<ParameterDTO>asList((ParameterDTO) new ParameterDTO(new Parameter("queryParameterName", Arrays.asList("queryParameterValue")))))
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
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "  \"url\" : \"http://www.example.com\"," + System.getProperty("line.separator") +
                "  \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "  \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"queryParameterName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"queryParameterValue\" ]" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"body\" : {" + System.getProperty("line.separator") +
                "    \"type\" : \"EXACT\"," + System.getProperty("line.separator") +
                "    \"value\" : \"somebody\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
                "  } ]," + System.getProperty("line.separator") +
                "  \"headers\" : [ {" + System.getProperty("line.separator") +
                "    \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "    \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "  } ]" + System.getProperty("line.separator") +
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
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"path\" : \"somePath\"" + System.getProperty("line.separator") +
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
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"path\" : \"somePath\"" + System.getProperty("line.separator") +
                "}", jsonExpectation);
    }
}
