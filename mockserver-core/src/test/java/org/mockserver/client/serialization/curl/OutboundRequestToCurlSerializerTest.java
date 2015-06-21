package org.mockserver.client.serialization.curl;

import org.junit.Test;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.OutboundHttpRequest;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.Parameter.param;

public class OutboundRequestToCurlSerializerTest {

    @Test
    public void shouldGenerateCurlForSimpleRequest() {
        // given
        OutboundRequestToCurlSerializer outboundRequestToCurlSerializer = new OutboundRequestToCurlSerializer();

        // when
        String curl = outboundRequestToCurlSerializer.toCurl(OutboundHttpRequest.outboundRequest("localhost", 80, null, request()));

        // then
        assertThat(curl, is("curl -v 'http://localhost:80/'"));
    }

    @Test
    public void shouldGenerateCurlForRequestWithPOST() {
        // given
        OutboundRequestToCurlSerializer outboundRequestToCurlSerializer = new OutboundRequestToCurlSerializer();

        // when
        String curl = outboundRequestToCurlSerializer.toCurl(OutboundHttpRequest.outboundRequest("localhost", 80, null,
                request()
                        .withMethod("POST")
        ));

        // then
        assertThat(curl, is("curl -v 'http://localhost:80/' -X POST"));
    }

    @Test
    public void shouldGenerateCurlForRequestWithGET() {
        // given
        OutboundRequestToCurlSerializer outboundRequestToCurlSerializer = new OutboundRequestToCurlSerializer();

        // when
        String curl = outboundRequestToCurlSerializer.toCurl(OutboundHttpRequest.outboundRequest("localhost", 80, null,
                request()
                        .withMethod("GET")
        ));

        // then
        assertThat(curl, is("curl -v 'http://localhost:80/'"));
    }

    @Test
    public void shouldGenerateCurlForRequestWithParameter() {
        // given
        OutboundRequestToCurlSerializer outboundRequestToCurlSerializer = new OutboundRequestToCurlSerializer();

        // when
        String curl = outboundRequestToCurlSerializer.toCurl(OutboundHttpRequest.outboundRequest("localhost", 80, null,
                request()
                        .withQueryStringParameters(
                                param("parameterName1", "parameterValue1_1", "parameterValue1_2"),
                                param("another parameter with spaces", "a value with single \'quotes\', double \"quotes\" and spaces")
                        )
        ));

        // then
        assertThat(curl, is("curl -v 'http://localhost:80/?parameterName1=parameterValue1_1&parameterName1=parameterValue1_2&another%20parameter%20with%20spaces=a%20value%20with%20single%20%27quotes%27%2C%20double%20%22quotes%22%20and%20spaces'"));
    }

    @Test
    public void shouldGenerateCurlForRequestWithHeaders() {
        // given
        OutboundRequestToCurlSerializer outboundRequestToCurlSerializer = new OutboundRequestToCurlSerializer();

        // when
        String curl = outboundRequestToCurlSerializer.toCurl(OutboundHttpRequest.outboundRequest("localhost", 80, null,
                request()
                        .withHeaders(
                                new Header("headerName1", "headerValue1"),
                                new Header("headerName2", "headerValue2_1", "headerValue2_2")
                        )
        ));

        // then
        assertThat(curl, is("curl -v 'http://localhost:80/' -H 'headerName1: headerValue1' -H 'headerName2: headerValue2_1' -H 'headerName2: headerValue2_2'"));
    }

    @Test
    public void shouldGenerateCurlForRequestWithCookies() {
        // given
        OutboundRequestToCurlSerializer outboundRequestToCurlSerializer = new OutboundRequestToCurlSerializer();

        // when
        String curl = outboundRequestToCurlSerializer.toCurl(OutboundHttpRequest.outboundRequest("localhost", 80, null,
                request()
                        .withCookies(
                                new Cookie("cookieName1", "cookieValue1"),
                                new Cookie("cookieName2", "cookieValue2")
                        )
        ));

        // then
        assertThat(curl, is("curl -v 'http://localhost:80/' -H 'Cookie: cookieName1=cookieValue1; cookieName2=cookieValue2'"));
    }

    @Test
    public void shouldGenerateCurlForRequestWithPOSTParameterHeadersAndCookies() {
        // given
        OutboundRequestToCurlSerializer outboundRequestToCurlSerializer = new OutboundRequestToCurlSerializer();

        // when
        String curl = outboundRequestToCurlSerializer.toCurl(OutboundHttpRequest.outboundRequest("localhost", 80, null,
                request()
                        .withPath("/somePath")
                        .withMethod("POST")
                        .withQueryStringParameters(
                                param("parameterName1", "parameterValue1_1", "parameterValue1_2"),
                                param("another parameter with spaces", "a value with single \'quotes\', double \"quotes\" and spaces")
                        )
                        .withHeaders(
                                new Header("headerName1", "headerValue1"),
                                new Header("headerName2", "headerValue2_1", "headerValue2_2")
                        )
                        .withCookies(
                                new Cookie("cookieName1", "cookieValue1"),
                                new Cookie("cookieName2", "cookieValue2")
                        )
        ));

        // then
        assertThat(curl, is("curl -v " +
                "'http://localhost:80/somePath" +
                "?parameterName1=parameterValue1_1&parameterName1=parameterValue1_2&another%20parameter%20with%20spaces=a%20value%20with%20single%20%27quotes%27%2C%20double%20%22quotes%22%20and%20spaces'" +
                " -X POST" +
                " -H 'headerName1: headerValue1'" +
                " -H 'headerName2: headerValue2_1'" +
                " -H 'headerName2: headerValue2_2'" +
                " -H 'Cookie: cookieName1=cookieValue1; cookieName2=cookieValue2'"));
    }

    @Test
    public void shouldHandleNullWhenGeneratingCurl() {
        // given
        OutboundRequestToCurlSerializer outboundRequestToCurlSerializer = new OutboundRequestToCurlSerializer();

        // when
        String curl = outboundRequestToCurlSerializer.toCurl(null);

        // then
        assertThat(curl, is(""));
    }

}