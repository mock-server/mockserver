package org.mockserver.serialization.curl;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;

import java.net.InetSocketAddress;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.Parameter.param;

public class HttpRequestToCurlSerializerTest {

    private final MockServerLogger mockServerLogger = new MockServerLogger();

    @Test
    public void shouldGenerateCurlForSimpleRequest() {
        // given
        HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer(mockServerLogger);

        // when
        String curl = httpRequestToCurlSerializer.toCurl(
                request(),
                new InetSocketAddress("localhost", 80)
        );

        // then
        assertThat(curl, is("curl -v 'http://localhost:80/'"));
    }

    @Test
    public void shouldGenerateCurlForRequestWithPOST() {
        // given
        HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer(mockServerLogger);

        // when
        String curl = httpRequestToCurlSerializer.toCurl(
                request()
                        .withMethod("POST"),
                new InetSocketAddress("localhost", 80)
        );

        // then
        assertThat(curl, is("curl -v 'http://localhost:80/' -X POST"));
    }

    @Test
    public void shouldGenerateCurlForRequestWithGETAndSocketAddress() {
        // given
        HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer(mockServerLogger);

        // when
        String curl = httpRequestToCurlSerializer.toCurl(
                request()
                        .withMethod("GET"),
                new InetSocketAddress("localhost", 80)
        );

        // then
        assertThat(curl, is("curl -v 'http://localhost:80/'"));
    }

    @Test
    public void shouldGenerateCurlForRequestWithGETAndNullSocketAddress() {
        // given
        HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer(mockServerLogger);

        // when
        String curl = httpRequestToCurlSerializer.toCurl(
                request()
                        .withHeader(HOST.toString(), "localhost:" + 80)
                        .withMethod("GET"),
                null
        );

        // then
        assertThat(curl, is("curl -v 'http://localhost:80/' -H 'host: localhost:80'"));
    }

    @Test
    public void shouldGenerateCurlForRequestWithGETAndNullSocketAddressAndNoHostHeader() {
        // given
        HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer(mockServerLogger);

        // when
        String curl = httpRequestToCurlSerializer.toCurl(
                request()
                        .withMethod("GET"),
                null
        );

        // then
        assertThat(curl, is("no host header or remote address specified"));
    }

    @Test
    public void shouldGenerateCurlForRequestWithParameter() {
        // given
        HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer(mockServerLogger);

        // when
        String curl = httpRequestToCurlSerializer.toCurl(
                request()
                        .withQueryStringParameters(
                                param("parameterName1", "parameterValue1_1", "parameterValue1_2"),
                                param("another parameter with spaces", "a value with single \'quotes\', double \"quotes\" and spaces")
                        ),
                new InetSocketAddress("localhost", 80)
        );

        // then
        assertThat(curl, is("curl -v 'http://localhost:80/?parameterName1=parameterValue1_1&parameterName1=parameterValue1_2&another%20parameter%20with%20spaces=a%20value%20with%20single%20%27quotes%27%2C%20double%20%22quotes%22%20and%20spaces'"));
    }

    @Test
    public void shouldGenerateCurlForRequestWithHeaders() {
        // given
        HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer(mockServerLogger);

        // when
        String curl = httpRequestToCurlSerializer.toCurl(
                request()
                        .withHeaders(
                                new Header("headerName1", "headerValue1"),
                                new Header("headerName2", "headerValue2_1", "headerValue2_2")
                        ),
                new InetSocketAddress("localhost", 80)
        );

        // then
        assertThat(curl, is("curl -v 'http://localhost:80/' -H 'headerName1: headerValue1' -H 'headerName2: headerValue2_1' -H 'headerName2: headerValue2_2'"));
    }

    @Test
    public void shouldGenerateCurlForRequestWithCookies() {
        // given
        HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer(mockServerLogger);

        // when
        String curl = httpRequestToCurlSerializer.toCurl(
                request()
                        .withCookies(
                                new Cookie("cookieName1", "cookieValue1"),
                                new Cookie("cookieName2", "cookieValue2")
                        ),
                new InetSocketAddress("localhost", 80)
        );

        // then
        assertThat(curl, is("curl -v 'http://localhost:80/' " +
                "-H 'cookie: cookieName1=cookieValue1; cookieName2=cookieValue2'"));
    }

    @Test
    public void shouldGenerateCurlForRequestWithPOSTParameterHeadersAndCookies() {
        // given
        HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer(mockServerLogger);

        // when
        String curl = httpRequestToCurlSerializer.toCurl(
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
                        ),
                new InetSocketAddress("localhost", 80)
        );

        // then
        assertThat(curl, is("curl -v " +
                "'http://localhost:80/somePath" +
                "?parameterName1=parameterValue1_1&parameterName1=parameterValue1_2&another%20parameter%20with%20spaces=a%20value%20with%20single%20%27quotes%27%2C%20double%20%22quotes%22%20and%20spaces'" +
                " -X POST" +
                " -H 'headerName1: headerValue1'" +
                " -H 'headerName2: headerValue2_1'" +
                " -H 'headerName2: headerValue2_2'" +
                " -H 'cookie: cookieName1=cookieValue1; cookieName2=cookieValue2'"));
    }

    @Test
    public void shouldHandleNullWhenGeneratingCurl() {
        // given
        HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer(mockServerLogger);

        // when
        String curl = httpRequestToCurlSerializer.toCurl(null, null);

        // then
        assertThat(curl, is("null HttpRequest"));
    }

}