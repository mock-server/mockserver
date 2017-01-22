package org.mockserver.client.serialization;

import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.Test;
import org.mockserver.client.serialization.model.*;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.Parameter;
import org.mockserver.model.StringBody;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.Delay.minutes;
import static org.mockserver.model.Delay.seconds;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class WebSocketMessageSerializerTest {

    @Test
    public void shouldDeserializeCompleteResponse() throws IOException, ClassNotFoundException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "  \"type\" : \"org.mockserver.model.HttpResponse\"," + System.getProperty("line.separator") +
                "  \"value\" : \"{" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "  \\\"statusCode\\\" : 123," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "  \\\"headers\\\" : [ {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "    \\\"name\\\" : \\\"someHeaderName\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "    \\\"values\\\" : [ \\\"someHeaderValue\\\" ]" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "  } ]," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "  \\\"cookies\\\" : [ {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "    \\\"name\\\" : \\\"someCookieName\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "    \\\"value\\\" : \\\"someCookieValue\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "  } ]," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "  \\\"body\\\" : \\\"somebody\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "  \\\"delay\\\" : {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "    \\\"timeUnit\\\" : \\\"SECONDS\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "    \\\"value\\\" : 5" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "  }" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +

                "}\"" + System.getProperty("line.separator") +
                "}";

        // when
        Object httpResponse = new WebSocketMessageSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpResponseDTO()
                .setStatusCode(123)
                .setBody(BodyDTO.createDTO(new StringBody("somebody")))
                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                .setDelay(new DelayDTO(seconds(5)))
                .buildObject(), httpResponse);
    }

    @Test
    public void shouldSerializeCompleteResponse() throws IOException {
        // when
        String jsonHttpResponse = new WebSocketMessageSerializer().serialize(
                new HttpResponseDTO()
                        .setStatusCode(123)
                        .setBody(BodyDTO.createDTO(new StringBody("somebody")))
                        .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                        .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                        .setDelay(new DelayDTO(minutes(1)))
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"type\" : \"org.mockserver.model.HttpResponse\"," + System.getProperty("line.separator") +
                "  \"value\" : \"{" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"statusCode\\\" : 123," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"headers\\\" : [ {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"name\\\" : \\\"someHeaderName\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"values\\\" : [ \\\"someHeaderValue\\\" ]" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  } ]," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"cookies\\\" : [ {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"name\\\" : \\\"someCookieName\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"value\\\" : \\\"someCookieValue\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  } ]," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"body\\\" : \\\"somebody\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"delay\\\" : {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"timeUnit\\\" : \\\"MINUTES\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"value\\\" : 1" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  }" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "}\"" + System.getProperty("line.separator") +
                "}", jsonHttpResponse);
    }

    @Test
    public void shouldDeserializeCompleteRequest() throws IOException, ClassNotFoundException {
        // given
        String requestBytes = "{" + System.getProperty("line.separator") +
                "  \"type\" : \"org.mockserver.model.HttpRequest\"," + System.getProperty("line.separator") +
                "  \"value\" : \"{" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"method\\\" : \\\"someMethod\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"path\\\" : \\\"somePath\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"queryStringParameters\\\" : [ {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"name\\\" : \\\"queryParameterName\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"values\\\" : [ \\\"queryParameterValue\\\" ]" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  } ]," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"headers\\\" : [ {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"name\\\" : \\\"someHeaderName\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"values\\\" : [ \\\"someHeaderValue\\\" ]" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  } ]," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"cookies\\\" : [ {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"name\\\" : \\\"someCookieName\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"value\\\" : \\\"someCookieValue\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  } ]," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"keepAlive\\\" : false," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"secure\\\" : true," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"body\\\" : \\\"somebody\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "}\"" + System.getProperty("line.separator") +
                "}";

        // when
        Object httpRequest = new WebSocketMessageSerializer().deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
                .setMethod(string("someMethod"))
                .setPath(string("somePath"))
                .setQueryStringParameters(Arrays.<ParameterDTO>asList((ParameterDTO) new ParameterDTO(new Parameter("queryParameterName", Arrays.asList("queryParameterValue")))))
                .setBody(BodyDTO.createDTO(new StringBody("somebody")))
                .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                .setSecure(true)
                .setKeepAlive(false)
                .buildObject(), httpRequest);
    }

    @Test
    public void shouldSerializeCompleteRequest() throws IOException {
        // when
        String jsonHttpRequest = new WebSocketMessageSerializer().serialize(
                new HttpRequestDTO()
                        .setMethod(string("someMethod"))
                        .setPath(string("somePath"))
                        .setQueryStringParameters(Arrays.<ParameterDTO>asList((ParameterDTO) new ParameterDTO(new Parameter("queryParameterName", Arrays.asList("queryParameterValue")))))
                        .setBody(BodyDTO.createDTO(new StringBody("somebody")))
                        .setHeaders(Arrays.<HeaderDTO>asList(new HeaderDTO(new Header("someHeaderName", Arrays.asList("someHeaderValue")))))
                        .setCookies(Arrays.<CookieDTO>asList(new CookieDTO(new Cookie("someCookieName", "someCookieValue"))))
                        .setSecure(true)
                        .setKeepAlive(false)
                        .buildObject()
        );

        // then
        assertEquals("{" + System.getProperty("line.separator") +
                "  \"type\" : \"org.mockserver.model.HttpRequest\"," + System.getProperty("line.separator") +
                "  \"value\" : \"{" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"method\\\" : \\\"someMethod\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"path\\\" : \\\"somePath\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"queryStringParameters\\\" : [ {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"name\\\" : \\\"queryParameterName\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"values\\\" : [ \\\"queryParameterValue\\\" ]" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  } ]," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"headers\\\" : [ {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"name\\\" : \\\"someHeaderName\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"values\\\" : [ \\\"someHeaderValue\\\" ]" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  } ]," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"cookies\\\" : [ {" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"name\\\" : \\\"someCookieName\\\"," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "    \\\"value\\\" : \\\"someCookieValue\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  } ]," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"keepAlive\\\" : false," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"secure\\\" : true," + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "  \\\"body\\\" : \\\"somebody\\\"" + StringEscapeUtils.escapeJava(System.getProperty("line.separator")) +
                "}\"" + System.getProperty("line.separator") +
                "}", jsonHttpRequest);
    }

}