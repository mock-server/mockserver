package org.mockserver.client.serialization;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.Test;
import org.mockserver.client.serialization.model.*;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Delay.minutes;
import static org.mockserver.model.Delay.seconds;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.StringBody.exact;

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
        Object httpResponse = new WebSocketMessageSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new HttpResponseDTO()
            .setStatusCode(123)
            .setBody(BodyWithContentTypeDTO.createDTO(exact("somebody")))
            .setHeaders(new Headers().withEntries(
                header("someHeaderName", "someHeaderValue")
            ))
            .setCookies(new Cookies().withEntries(
                cookie("someCookieName", "someCookieValue")
            ))
            .setDelay(new DelayDTO(seconds(5)))
            .buildObject(), httpResponse);
    }

    @Test
    public void shouldSerializeCompleteResponse() throws IOException {
        // when
        String jsonHttpResponse = new WebSocketMessageSerializer(new MockServerLogger()).serialize(
            new HttpResponseDTO()
                .setStatusCode(123)
                .setBody(BodyWithContentTypeDTO.createDTO(exact("somebody")))
                .setHeaders(new Headers().withEntries(
                    header("someHeaderName", "someHeaderValue")
                ))
                .setCookies(new Cookies().withEntries(
                    cookie("someCookieName", "someCookieValue")
                ))
                .setDelay(new DelayDTO(minutes(1)))
                .buildObject()
        );

        // then
        assertEquals("{" + NEW_LINE +
            "  \"type\" : \"org.mockserver.model.HttpResponse\"," + NEW_LINE +
            "  \"value\" : \"{" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  \\\"statusCode\\\" : 123," + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  \\\"headers\\\" : {" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "    \\\"someHeaderName\\\" : [ \\\"someHeaderValue\\\" ]" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  }," + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  \\\"cookies\\\" : {" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "    \\\"someCookieName\\\" : \\\"someCookieValue\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  }," + StringEscapeUtils.escapeJava(NEW_LINE) +
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
            "  \\\"queryStringParameters\\\" : {" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "    \\\"queryParameterName\\\" : [ \\\"queryParameterValue\\\" ]" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  }," + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  \\\"headers\\\" : {" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "    \\\"someHeaderName\\\" : [ \\\"someHeaderValue\\\" ]" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  }," + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  \\\"cookies\\\" : {" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "    \\\"someCookieName\\\" : \\\"someCookieValue\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  }," + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  \\\"keepAlive\\\" : false," + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  \\\"secure\\\" : true," + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  \\\"body\\\" : \\\"somebody\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "}\"" + NEW_LINE +
            "}";

        // when
        Object httpRequest = new WebSocketMessageSerializer(new MockServerLogger()).deserialize(requestBytes);

        // then
        assertEquals(new HttpRequestDTO()
            .setMethod(string("someMethod"))
            .setPath(string("somePath"))
            .setQueryStringParameters(new Parameters().withEntries(
                param("queryParameterName", "queryParameterValue")
            ))
            .setBody(BodyDTO.createDTO(exact("somebody")))
            .setHeaders(new Headers().withEntries(
                header("someHeaderName", "someHeaderValue")
            ))
            .setCookies(new Cookies().withEntries(
                cookie("someCookieName", "someCookieValue")
            ))
            .setSecure(true)
            .setKeepAlive(false)
            .buildObject(), httpRequest);
    }

    @Test
    public void shouldSerializeCompleteRequest() throws IOException {
        // when
        String jsonHttpRequest = new WebSocketMessageSerializer(new MockServerLogger()).serialize(
            new HttpRequestDTO()
                .setMethod(string("someMethod"))
                .setPath(string("somePath"))
                .setQueryStringParameters(new Parameters().withEntries(
                    param("queryParameterName", "queryParameterValue")
                ))
                .setBody(BodyDTO.createDTO(exact("somebody")))
                .setHeaders(new Headers().withEntries(
                    header("someHeaderName", "someHeaderValue")
                ))
                .setCookies(new Cookies().withEntries(
                    cookie("someCookieName", "someCookieValue")
                ))
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
            "  \\\"queryStringParameters\\\" : {" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "    \\\"queryParameterName\\\" : [ \\\"queryParameterValue\\\" ]" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  }," + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  \\\"headers\\\" : {" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "    \\\"someHeaderName\\\" : [ \\\"someHeaderValue\\\" ]" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  }," + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  \\\"cookies\\\" : {" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "    \\\"someCookieName\\\" : \\\"someCookieValue\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  }," + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  \\\"keepAlive\\\" : false," + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  \\\"secure\\\" : true," + StringEscapeUtils.escapeJava(NEW_LINE) +
            "  \\\"body\\\" : \\\"somebody\\\"" + StringEscapeUtils.escapeJava(NEW_LINE) +
            "}\"" + NEW_LINE +
            "}", jsonHttpRequest);
    }

}
