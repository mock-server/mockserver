package org.mockserver.integration.server;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.http.HttpRequestClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientServerIntegrationTest {

    private final HttpRequestClient httpRequestClient;
    protected MockServerClient mockServerClient;

    public AbstractClientServerIntegrationTest() {
        httpRequestClient = new HttpRequestClient("http://127.0.0.1:" + getPort());
    }

    public abstract int getPort();

    @Before
    public void createClient() {
        mockServerClient = new MockServerClient("localhost", getPort());
    }

    @Test
    public void clientCanCallServer() {
        // when
        mockServerClient.when(new HttpRequest()).respond(new HttpResponse().withBody("some_body"));

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(new HttpRequest()));
    }

    @Test
    public void clientCanCallServerMatchPath() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withPath("/some_path1")
                )
                .respond(
                        new HttpResponse()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        new HttpRequest()
                                .withPath("/some_path2")
                )
                .respond(
                        new HttpResponse()
                                .withBody("some_body2")
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(new HttpRequest().withPath("/some_path2")));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(new HttpRequest().withPath("/some_path1")));
    }

    @Test
    public void clientCanCallServerMatchPathXTimes() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path"), Times.exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(new HttpRequest().withPath("/some_path")));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(new HttpRequest().withPath("/some_path")));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(new HttpRequest().withPath("/some_path")));
    }

    @Test
    public void clientCanCallServerMatchPathWithDelay() {
        // when
        mockServerClient.when(
                new HttpRequest()
                        .withPath("/some_path1")
        ).respond(
                new HttpResponse()
                        .withBody("some_body1")
                        .withDelay(new Delay(TimeUnit.MILLISECONDS, 100))
        );
        mockServerClient.when(
                new HttpRequest()
                        .withPath("/some_path2")
        ).respond(
                new HttpResponse()
                        .withBody("some_body2")
                        .withDelay(new Delay(TimeUnit.SECONDS, 1))
        );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(new HttpRequest().withPath("/some_path2")));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(new HttpRequest().withPath("/some_path1")));
    }

    @Test
    public void clientCanCallServerPositiveMatchForGETAndMatchingPath() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse"),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForGETAndMatchingPathAndBody() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                                .withBody("some_bodyRequest")
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                                .withHeaders(new Header("headerNameResponse", "headerValueResponse"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withHeaders(
                                new Header("headerNameResponse", "headerValueResponse")
                        ),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForGETAndMatchingPathAndParameters() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                                .withQueryString("parameterName=parameterValue")
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                new Header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForGETAndMatchingPathBodyAndParameters() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_bodyRequest")
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                                .withHeaders(new Header("headerNameResponse", "headerValueResponse"))
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                new Header("headerNameResponse", "headerValueResponse"),
                                new Header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForGETAndMatchingPathBodyHeadersAndParameters() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                                .withHeaders(new Header("headerNameResponse", "headerValueResponse"))
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                new Header("headerNameResponse", "headerValueResponse"),
                                new Header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForGETAndMatchingPathBodyHeadersCookiesAndParameters() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                                .withHeaders(new Header("headerNameResponse", "headerValueResponse"))
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                new Header("headerNameResponse", "headerValueResponse"),
                                new Header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForPOSTAndMatchingPathBodyAndParameters() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("POST")
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("bodyParameterName=bodyParameterValue")
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("bodyParameterName=bodyParameterValue")
                )
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForPOSTAndMatchingPathAndParameters() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("POST")
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withPath("/some_path")
                                .withBody("bodyParameterName=bodyParameterValue")
                                .withQueryString("parameterName=parameterValue")
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchBodyOnly() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("someotherbody")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchPathOnly() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withPath("/someotherpath")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchParameterNameOnly() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withQueryString("parameterOtherName=parameterValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchParameterValueOnly() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterOtherValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchCookieNameOnly() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieOtherName", "cookieValue"))
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchCookieValueOnly() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieOtherValue"))
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchHeaderNameOnly() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerOtherName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchHeaderValueOnly() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withPath("/some_path")
                                .withQueryString("parameterName=parameterValue")
                                .withBody("some_body")
                                .withHeaders(new Header("headerName", "headerOtherValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
        );
    }

    @Test
    public void clientCanClearServerExpectations() {
        // given
        mockServerClient
                .when(
                        new HttpRequest()
                                .withPath("/some_path1")
                )
                .respond(
                        new HttpResponse()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        new HttpRequest()
                                .withPath("/some_path2")
                )
                .respond(
                        new HttpResponse()
                                .withBody("some_body2")
                );

        // when
        mockServerClient
                .clear(
                        new HttpRequest()
                                .withPath("/some_path1")
                );

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(new HttpRequest().withPath("/some_path2")));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(new HttpRequest().withPath("/some_path1")));
    }

    @Test
    public void clientCanResetServerExpectations() {
        // given
        mockServerClient
                .when(
                        new HttpRequest()
                                .withPath("/some_path1")
                )
                .respond(
                        new HttpResponse()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        new HttpRequest()
                                .withPath("/some_path2")
                )
                .respond(
                        new HttpResponse()
                                .withBody("some_body2")
                );

        // when
        mockServerClient.reset();

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(new HttpRequest().withPath("/some_path1")));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(new HttpRequest().withPath("/some_path2")));
    }

    protected HttpResponse makeRequest(HttpRequest httpRequest) {
        try {
            HttpResponse httpResponse = httpRequestClient.sendRequest(httpRequest);
            for (Header header : new ArrayList<>(httpResponse.getHeaders())) {
                if (header.getName().equals("Server") || header.getName().equals("Expires")) {
                    httpResponse.getHeaders().remove(header);
                }
            }
            return httpResponse;
        } catch (Exception e) {
            throw new RuntimeException("Error making http request", e);
        }
    }
}
