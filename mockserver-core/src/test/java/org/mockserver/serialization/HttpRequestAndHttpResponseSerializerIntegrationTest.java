package org.mockserver.serialization;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.StringBody.exact;

/**
 * @author jamesdbloom
 */
public class HttpRequestAndHttpResponseSerializerIntegrationTest {

    private final HttpRequestAndHttpResponse completeHttpRequestAndHttpResponse =
        new HttpRequestAndHttpResponse()
            .withHttpRequest(
                new HttpRequest()
                    .withMethod("GET")
                    .withPath("somepath")
                    .withPathParameters(
                        new Parameter("pathParameterNameOne", "pathParameterValueOne_One", "pathParameterValueOne_Two"),
                        new Parameter("pathParameterNameTwo", "pathParameterValueTwo_One")
                    )
                    .withQueryStringParameters(
                        new Parameter("queryStringParameterNameOne", "queryStringParameterValueOne_One", "queryStringParameterValueOne_Two"),
                        new Parameter("queryStringParameterNameTwo", "queryStringParameterValueTwo_One")
                    )
                    .withBody(new StringBody("someBody"))
                    .withHeaders(new Header("headerName", "headerValue"))
                    .withCookies(new Cookie("cookieName", "cookieValue"))
                    .withSecure(true)
                    .withSocketAddress("someHost", 1234, SocketAddress.Scheme.HTTPS)
                    .withKeepAlive(true)
            )
            .withHttpResponse(
                new HttpResponse()
                    .withStatusCode(123)
                    .withReasonPhrase("randomPhrase")
                    .withBody(exact("somebody"))
                    .withHeaders(header("headerName", "headerValue"))
                    .withCookies(cookie("cookieName", "cookieValue"))
                    .withDelay(new Delay(TimeUnit.MICROSECONDS, 3))
            );
    private final String completeSerialisedHttpRequestAndHttpResponse = "{" + NEW_LINE +
        "  \"httpRequest\" : {" + NEW_LINE +
        "    \"method\" : \"GET\"," + NEW_LINE +
        "    \"path\" : \"somepath\"," + NEW_LINE +
        "    \"pathParameters\" : {" + NEW_LINE +
        "      \"pathParameterNameTwo\" : [ \"pathParameterValueTwo_One\" ]," + NEW_LINE +
        "      \"pathParameterNameOne\" : [ \"pathParameterValueOne_One\", \"pathParameterValueOne_Two\" ]" + NEW_LINE +
        "    }," + NEW_LINE +
        "    \"queryStringParameters\" : {" + NEW_LINE +
        "      \"queryStringParameterNameTwo\" : [ \"queryStringParameterValueTwo_One\" ]," + NEW_LINE +
        "      \"queryStringParameterNameOne\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
        "    }," + NEW_LINE +
        "    \"headers\" : {" + NEW_LINE +
        "      \"headerName\" : [ \"headerValue\" ]" + NEW_LINE +
        "    }," + NEW_LINE +
        "    \"cookies\" : {" + NEW_LINE +
        "      \"cookieName\" : \"cookieValue\"" + NEW_LINE +
        "    }," + NEW_LINE +
        "    \"keepAlive\" : true," + NEW_LINE +
        "    \"secure\" : true," + NEW_LINE +
        "    \"socketAddress\" : {" + NEW_LINE +
        "      \"host\" : \"someHost\"," + NEW_LINE +
        "      \"port\" : 1234," + NEW_LINE +
        "      \"scheme\" : \"HTTPS\"" + NEW_LINE +
        "    }," + NEW_LINE +
        "    \"body\" : \"someBody\"" + NEW_LINE +
        "  }," + NEW_LINE +
        "  \"httpResponse\" : {" + NEW_LINE +
        "    \"statusCode\" : 123," + NEW_LINE +
        "    \"reasonPhrase\" : \"randomPhrase\"," + NEW_LINE +
        "    \"headers\" : {" + NEW_LINE +
        "      \"headerName\" : [ \"headerValue\" ]" + NEW_LINE +
        "    }," + NEW_LINE +
        "    \"cookies\" : {" + NEW_LINE +
        "      \"cookieName\" : \"cookieValue\"" + NEW_LINE +
        "    }," + NEW_LINE +
        "    \"body\" : \"somebody\"," + NEW_LINE +
        "    \"delay\" : {" + NEW_LINE +
        "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
        "      \"value\" : 3" + NEW_LINE +
        "    }" + NEW_LINE +
        "  }" + NEW_LINE +
        "}";

    @Test
    public void shouldDeserializeCompleteObject() {
        // when
        HttpRequestAndHttpResponse httpRequestAndHttpResponse = new HttpRequestAndHttpResponseSerializer(new MockServerLogger()).deserialize(completeSerialisedHttpRequestAndHttpResponse);

        // then
        assertEquals(completeHttpRequestAndHttpResponse, httpRequestAndHttpResponse);
    }

    @Test
    public void shouldSerializeArray() {
        // when
        String jsonHttpRequestAndHttpResponse = new HttpRequestAndHttpResponseSerializer(new MockServerLogger()).serialize(
            completeHttpRequestAndHttpResponse,
            completeHttpRequestAndHttpResponse);

        // then
        assertEquals("[ " + completeSerialisedHttpRequestAndHttpResponse + ", " + completeSerialisedHttpRequestAndHttpResponse + " ]", jsonHttpRequestAndHttpResponse);
    }

    @Test
    public void shouldSerializeList() {
        // when
        String jsonHttpRequestAndHttpResponse = new HttpRequestAndHttpResponseSerializer(new MockServerLogger()).serialize(
            Arrays.asList(
                completeHttpRequestAndHttpResponse,
                completeHttpRequestAndHttpResponse
            )
        );

        // then
        assertEquals("[ " + completeSerialisedHttpRequestAndHttpResponse + ", " + completeSerialisedHttpRequestAndHttpResponse + " ]", jsonHttpRequestAndHttpResponse);
    }

    @Test
    public void shouldSerializeCompleteObject() {
        // when
        String jsonHttpRequestAndHttpResponse = new HttpRequestAndHttpResponseSerializer(new MockServerLogger()).serialize(
            completeHttpRequestAndHttpResponse
        );

        // then
        assertEquals(completeSerialisedHttpRequestAndHttpResponse, jsonHttpRequestAndHttpResponse);
    }

}
