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
import static org.mockserver.character.Character.NEW_LINE;
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
        String requestBytes = "{" + NEW_LINE +
                "  \"type\" : \"org.mockserver.model.HttpResponse\"," + NEW_LINE +
                "  \"value\" : \"{" + StringEscapeUtils.escapeJava(NEW_LINE) +

                "  \\\"statusCode\\\" : 123," + StringEscapeUtils.escapeJava(NEW_LINE) +

                "  \\\"headers\\\" : [ {" + StringEscapeUtils.escapeJava(NEW_LINE) +

                "    \\\"name\\\" : \\\"someHeaderName\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +

                "    \\\"values\\\" : [ \\\"someHeaderValue\\\" ]" + StringEscapeUtils.escapeJava(NEW_LINE) +

                "  } ]," + StringEscapeUtils.escapeJava(NEW_LINE) +

                "  \\\"cookies\\\" : [ {" + StringEscapeUtils.escapeJava(NEW_LINE) +

                "    \\\"name\\\" : \\\"someCookieName\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +

                "    \\\"value\\\" : \\\"someCookieValue\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +

                "  } ]," + StringEscapeUtils.escapeJava(NEW_LINE) +

                "  \\\"body\\\" : \\\"somebody\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +

                "  \\\"delay\\\" : {" + StringEscapeUtils.escapeJava(NEW_LINE) +

                "    \\\"timeUnit\\\" : \\\"SECONDS\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +

                "    \\\"value\\\" : 5" + StringEscapeUtils.escapeJava(NEW_LINE) +

                "  }" + StringEscapeUtils.escapeJava(NEW_LINE) +

                "}\"" + NEW_LINE +
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
        assertEquals("{" + NEW_LINE +
                "  \"type\" : \"org.mockserver.model.HttpResponse\"," + NEW_LINE +
                "  \"value\" : \"{" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"statusCode\\\" : 123," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"headers\\\" : [ {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"name\\\" : \\\"someHeaderName\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"values\\\" : [ \\\"someHeaderValue\\\" ]" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  } ]," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"cookies\\\" : [ {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"name\\\" : \\\"someCookieName\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"value\\\" : \\\"someCookieValue\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  } ]," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"body\\\" : \\\"somebody\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"delay\\\" : {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"timeUnit\\\" : \\\"MINUTES\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"value\\\" : 1" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  }" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "}\"" + NEW_LINE +
                "}", jsonHttpResponse);
    }

    @Test
    public void shouldDeserializeCompleteRequest() throws IOException, ClassNotFoundException {
        // given
        String requestBytes = "{" + NEW_LINE +
                "  \"type\" : \"org.mockserver.model.HttpRequest\"," + NEW_LINE +
                "  \"value\" : \"{" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"method\\\" : \\\"someMethod\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"path\\\" : \\\"somePath\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"queryStringParameters\\\" : [ {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"name\\\" : \\\"queryParameterName\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"values\\\" : [ \\\"queryParameterValue\\\" ]" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  } ]," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"headers\\\" : [ {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"name\\\" : \\\"someHeaderName\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"values\\\" : [ \\\"someHeaderValue\\\" ]" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  } ]," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"cookies\\\" : [ {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"name\\\" : \\\"someCookieName\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"value\\\" : \\\"someCookieValue\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  } ]," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"keepAlive\\\" : false," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"secure\\\" : true," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"body\\\" : \\\"somebody\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "}\"" + NEW_LINE +
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
        assertEquals("{" + NEW_LINE +
                "  \"type\" : \"org.mockserver.model.HttpRequest\"," + NEW_LINE +
                "  \"value\" : \"{" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"method\\\" : \\\"someMethod\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"path\\\" : \\\"somePath\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"queryStringParameters\\\" : [ {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"name\\\" : \\\"queryParameterName\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"values\\\" : [ \\\"queryParameterValue\\\" ]" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  } ]," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"headers\\\" : [ {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"name\\\" : \\\"someHeaderName\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"values\\\" : [ \\\"someHeaderValue\\\" ]" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  } ]," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"cookies\\\" : [ {" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"name\\\" : \\\"someCookieName\\\"," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "    \\\"value\\\" : \\\"someCookieValue\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  } ]," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"keepAlive\\\" : false," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"secure\\\" : true," + StringEscapeUtils.escapeJava(NEW_LINE) +
                "  \\\"body\\\" : \\\"somebody\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
                "}\"" + NEW_LINE +
                "}", jsonHttpRequest);
    }

}