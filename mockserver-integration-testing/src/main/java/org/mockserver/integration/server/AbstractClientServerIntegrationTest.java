package org.mockserver.integration.server;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockserver.configuration.SystemProperties.bufferSize;
import static org.mockserver.configuration.SystemProperties.maxTimeout;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientServerIntegrationTest {

    protected static MockServerClient mockServerClient;
    protected static String servletContext = "";
    private final ApacheHttpClient apacheHttpClient;

    public AbstractClientServerIntegrationTest() {
        bufferSize(1024);
        maxTimeout(TimeUnit.SECONDS.toMillis(10));
        apacheHttpClient = new ApacheHttpClient();
    }

    public abstract int getPort();

    public abstract int getSecurePort();

    @Before
    public void resetServer() {
        mockServerClient.reset();
    }

    @Test
    public void clientCanCallServer() {
        // when
        mockServerClient.when(new HttpRequest()).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "")
                ));
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : ""))
                ));
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
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2")
                ));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1")
                ));
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2")
                ));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1")
                ));
    }

    @Test
    public void clientCanCallServerMatchPathXTimes() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path"), Times.exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path")
                ));
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path")
                ));
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path")
                ));
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path")
                ));
    }

    @Test
    public void clientCanVerifyRequestsReceived() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path"), Times.exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path")
                ));
        mockServerClient.verify(new HttpRequest()
                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                .withPath("/some_path"));
        mockServerClient.verify(new HttpRequest()
                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                .withPath("/some_path"), org.mockserver.client.proxy.Times.exactly(1));

        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path")
                ));
        mockServerClient.verify(new HttpRequest()
                .withURL("https{0,1}\\:\\/\\/localhost\\:\\d*\\/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("\\/") ? "\\/" : "") + "some_path")
                .withPath("/some_path"), org.mockserver.client.proxy.Times.atLeast(1));
        mockServerClient.verify(new HttpRequest()
                .withURL("https{0,1}\\:\\/\\/localhost\\:\\d*\\/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("\\/") ? "\\/" : "") + "some_path")
                .withPath("/some_path"), org.mockserver.client.proxy.Times.exactly(2));
    }

    @Test(expected = AssertionError.class)
    public void clientCanVerifyNotEnoughRequestsReceived() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path"), Times.exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path")
                ));
        mockServerClient.verify(new HttpRequest()
                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                .withPath("/some_path"), org.mockserver.client.proxy.Times.atLeast(2));
    }

    @Test(expected = AssertionError.class)
    public void clientCanVerifyTooManyRequestsReceived() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path"), Times.exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path")
                ));
        mockServerClient.verify(new HttpRequest()
                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                .withPath("/some_path"), org.mockserver.client.proxy.Times.exactly(0));
    }

    @Test(expected = AssertionError.class)
    public void clientCanVerifyNotMatchingRequestsReceived() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path"), Times.exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path")
                ));
        mockServerClient.verify(new HttpRequest()
                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_other_path")
                .withPath("/some_other_path"), org.mockserver.client.proxy.Times.exactly(2));
    }

    @Test
    public void clientCanCallServerMatchBodyWithXPath() {
        // when
        mockServerClient.when(new HttpRequest().withBody(new StringBody("/bookstore/book[price>35]/price", Body.Type.XPATH)), Times.exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withMethod("POST")
                                .withBody(new StringBody("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" +
                                        "\n" +
                                        "<bookstore>\n" +
                                        "\n" +
                                        "<book category=\"COOKING\">\n" +
                                        "  <title lang=\"en\">Everyday Italian</title>\n" +
                                        "  <author>Giada De Laurentiis</author>\n" +
                                        "  <year>2005</year>\n" +
                                        "  <price>30.00</price>\n" +
                                        "</book>\n" +
                                        "\n" +
                                        "<book category=\"CHILDREN\">\n" +
                                        "  <title lang=\"en\">Harry Potter</title>\n" +
                                        "  <author>J K. Rowling</author>\n" +
                                        "  <year>2005</year>\n" +
                                        "  <price>29.99</price>\n" +
                                        "</book>\n" +
                                        "\n" +
                                        "<book category=\"WEB\">\n" +
                                        "  <title lang=\"en\">Learning XML</title>\n" +
                                        "  <author>Erik T. Ray</author>\n" +
                                        "  <year>2003</year>\n" +
                                        "  <price>39.95</price>\n" +
                                        "</book>\n" +
                                        "\n" +
                                        "</bookstore>", Body.Type.EXACT))
                ));
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withMethod("POST")
                                .withBody(new StringBody("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" +
                                        "\n" +
                                        "<bookstore>\n" +
                                        "\n" +
                                        "<book category=\"COOKING\">\n" +
                                        "  <title lang=\"en\">Everyday Italian</title>\n" +
                                        "  <author>Giada De Laurentiis</author>\n" +
                                        "  <year>2005</year>\n" +
                                        "  <price>30.00</price>\n" +
                                        "</book>\n" +
                                        "\n" +
                                        "<book category=\"CHILDREN\">\n" +
                                        "  <title lang=\"en\">Harry Potter</title>\n" +
                                        "  <author>J K. Rowling</author>\n" +
                                        "  <year>2005</year>\n" +
                                        "  <price>29.99</price>\n" +
                                        "</book>\n" +
                                        "\n" +
                                        "<book category=\"WEB\">\n" +
                                        "  <title lang=\"en\">Learning XML</title>\n" +
                                        "  <author>Erik T. Ray</author>\n" +
                                        "  <year>2003</year>\n" +
                                        "  <price>39.95</price>\n" +
                                        "</book>\n" +
                                        "\n" +
                                        "</bookstore>", Body.Type.EXACT))
                ));
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
                        .withDelay(new Delay(TimeUnit.MILLISECONDS, 10))
        );
        mockServerClient.when(
                new HttpRequest()
                        .withPath("/some_path2")
        ).respond(
                new HttpResponse()
                        .withBody("some_body2")
                        .withDelay(new Delay(TimeUnit.MILLISECONDS, 20))
        );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2")
                ));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1")
                ));
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2")
                ));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1")
                ));
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
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse"),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse"),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
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
                                .withMethod("POST")
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
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withHeaders(
                                new Header("headerNameResponse", "headerValueResponse")
                        ),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withHeaders(
                                new Header("headerNameResponse", "headerValueResponse")
                        ),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
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
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // - in http
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
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
        // - in https
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
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForGETAndMatchingPathAndHeaders() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        new Header("requestHeaderNameOne", "requestHeaderValueOne_One", "requestHeaderValueOne_Two"),
                                        new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                )
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // - in http
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
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        new Header("requestHeaderNameOne", "requestHeaderValueOne_One", "requestHeaderValueOne_Two"),
                                        new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                )
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
        // - in https
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
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        new Header("requestHeaderNameOne", "requestHeaderValueOne_One", "requestHeaderValueOne_Two"),
                                        new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                )
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForGETAndMatchingPathAndCookies() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                                .withCookies(
                                        new Cookie("requestCookieNameOne", "requestCookieValueOne_One", "requestCookieValueOne_Two"),
                                        new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                )
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                                .withCookies(
                                        new Cookie("responseCookieNameOne", "responseCookieValueOne_One", "responseCookieValueOne_Two"),
                                        new Cookie("responseCookieNameTwo", "responseCookieValueTwo")
                                )
                );

        // then
        // - in http - cookie objects
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(
                                new Cookie("responseCookieNameOne", "responseCookieValueOne_One", "responseCookieValueOne_Two"),
                                new Cookie("responseCookieNameTwo", "responseCookieValueTwo")
                        )
                        .withHeaders(
                                new Header("Set-Cookie", "responseCookieNameOne=responseCookieValueOne_One", "responseCookieNameOne=responseCookieValueOne_Two", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        new Header("headerNameRequest", "headerValueRequest")
                                )
                                .withCookies(
                                        new Cookie("requestCookieNameOne", "requestCookieValueOne_One", "requestCookieValueOne_Two"),
                                        new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                )
                )
        );
        // - in http - cookie header
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(
                                new Cookie("responseCookieNameOne", "responseCookieValueOne_One", "responseCookieValueOne_Two"),
                                new Cookie("responseCookieNameTwo", "responseCookieValueTwo")
                        )
                        .withHeaders(
                                new Header("Set-Cookie", "responseCookieNameOne=responseCookieValueOne_One", "responseCookieNameOne=responseCookieValueOne_Two", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        new Header("headerNameRequest", "headerValueRequest"),
                                        new Header("Cookie", "requestCookieNameOne=requestCookieValueOne_One; requestCookieNameOne=requestCookieValueOne_Two; requestCookieNameTwo=requestCookieValueTwo")
                                )
                )
        );
        // - in https - cookie objects
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(
                                new Cookie("responseCookieNameOne", "responseCookieValueOne_One", "responseCookieValueOne_Two"),
                                new Cookie("responseCookieNameTwo", "responseCookieValueTwo")
                        )
                        .withHeaders(
                                new Header("Set-Cookie", "responseCookieNameOne=responseCookieValueOne_One", "responseCookieNameOne=responseCookieValueOne_Two", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        new Header("headerNameRequest", "headerValueRequest")
                                )
                                .withCookies(
                                        new Cookie("requestCookieNameOne", "requestCookieValueOne_One", "requestCookieValueOne_Two"),
                                        new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                )
                )
        );
        // - in https - cookie header
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(
                                new Cookie("responseCookieNameOne", "responseCookieValueOne_One", "responseCookieValueOne_Two"),
                                new Cookie("responseCookieNameTwo", "responseCookieValueTwo")
                        )
                        .withHeaders(
                                new Header("Set-Cookie", "responseCookieNameOne=responseCookieValueOne_One", "responseCookieNameOne=responseCookieValueOne_Two", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        new Header("headerNameRequest", "headerValueRequest"),
                                        new Header("Cookie", "requestCookieNameOne=requestCookieValueOne_One; requestCookieNameOne=requestCookieValueOne_Two; requestCookieNameTwo=requestCookieValueTwo")
                                )
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
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                );

        // then
        // - in http - url query string
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path" +
                                        "?queryStringParameterOneName=queryStringParameterOneValueOne" +
                                        "&queryStringParameterOneName=queryStringParameterOneValueTwo" +
                                        "&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withBody(new ParameterBody(new Parameter("bodyParameterName", "bodyParameterValue"))))
        );
        // - in https - query string parameter objects
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new ParameterBody(new Parameter("bodyParameterName=bodyParameterValue")))
                )
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForPOSTAndMatchingPathBodyAndQueryParameters() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("POST")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
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
        // - in http - url query string
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
                                .withMethod("POST")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest" +
                                        "?queryStringParameterOneName=queryStringParameterOneValueOne" +
                                        "&queryStringParameterOneName=queryStringParameterOneValueTwo" +
                                        "&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")))
        );
        // - in http - query string parameter objects
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
                                .withMethod("POST")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")))
        );
        // - in https - url string and query string parameter objects
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
                                .withMethod("POST")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest" +
                                        "?queryStringParameterOneName=queryStringParameterOneValueOne" +
                                        "&queryStringParameterOneName=queryStringParameterOneValueTwo" +
                                        "&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")))
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForPOSTAndMatchingPathBodyParametersAndQueryParameters() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("POST")
                                .withPath("/some_pathRequest")
                                .withBody(new ParameterBody(
                                        new Parameter("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                                .withHeaders(new Header("headerNameResponse", "headerValueResponse"))
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // - in http - body string
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
                                .withMethod("POST")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.EXACT))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
        // - in http - body parameter objects
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
                                .withMethod("POST")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new ParameterBody(
                                        new Parameter("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
        // - in https - url string and query string parameter objects
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
                                .withMethod("POST")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest" +
                                        "?bodyParameterOneName=bodyParameterOneValueOne" +
                                        "&bodyParameterOneName=bodyParameterOneValueTwo" +
                                        "&bodyParameterTwoName=bodyParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withBody(new ParameterBody(
                                        new Parameter("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForPUTAndMatchingPathBodyParametersAndHeadersAndCookies() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("PUT")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.EXACT))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")))
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                                .withHeaders(new Header("headerNameResponse", "headerValueResponse"))
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // - in http - body string
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
                                .withMethod("PUT")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.EXACT))
                                .withHeaders(
                                        new Header("headerNameRequest", "headerValueRequest"),
                                        new Header("Cookie", "cookieNameRequest=cookieValueRequest")
                                )
                )
        );
        // - in http - body parameter objects
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
                                .withMethod("PUT")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new ParameterBody(
                                        new Parameter("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
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
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
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
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_other_body", Body.Type.EXACT))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_other_body", Body.Type.EXACT))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchXPathBodyOnly() {
        // when
        mockServerClient.when(new HttpRequest().withBody(new StringBody("/bookstore/book[price>35]/price", Body.Type.XPATH)), Times.exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withMethod("POST")
                                .withBody(new StringBody("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" +
                                        "\n" +
                                        "<bookstore>\n" +
                                        "\n" +
                                        "<book category=\"COOKING\">\n" +
                                        "  <title lang=\"en\">Everyday Italian</title>\n" +
                                        "  <author>Giada De Laurentiis</author>\n" +
                                        "  <year>2005</year>\n" +
                                        "  <price>30.00</price>\n" +
                                        "</book>\n" +
                                        "\n" +
                                        "<book category=\"CHILDREN\">\n" +
                                        "  <title lang=\"en\">Harry Potter</title>\n" +
                                        "  <author>J K. Rowling</author>\n" +
                                        "  <year>2005</year>\n" +
                                        "  <price>29.99</price>\n" +
                                        "</book>\n" +
                                        "\n" +
                                        "<book category=\"WEB\">\n" +
                                        "  <title lang=\"en\">Learning XML</title>\n" +
                                        "  <author>Erik T. Ray</author>\n" +
                                        "  <year>2003</year>\n" +
                                        "  <price>31.95</price>\n" +
                                        "</book>\n" +
                                        "\n" +
                                        "</bookstore>", Body.Type.EXACT))
                ));
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withMethod("POST")
                                .withBody(new StringBody("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" +
                                        "\n" +
                                        "<bookstore>\n" +
                                        "\n" +
                                        "<book category=\"COOKING\">\n" +
                                        "  <title lang=\"en\">Everyday Italian</title>\n" +
                                        "  <author>Giada De Laurentiis</author>\n" +
                                        "  <year>2005</year>\n" +
                                        "  <price>30.00</price>\n" +
                                        "</book>\n" +
                                        "\n" +
                                        "<book category=\"CHILDREN\">\n" +
                                        "  <title lang=\"en\">Harry Potter</title>\n" +
                                        "  <author>J K. Rowling</author>\n" +
                                        "  <year>2005</year>\n" +
                                        "  <price>29.99</price>\n" +
                                        "</book>\n" +
                                        "\n" +
                                        "<book category=\"WEB\">\n" +
                                        "  <title lang=\"en\">Learning XML</title>\n" +
                                        "  <author>Erik T. Ray</author>\n" +
                                        "  <year>2003</year>\n" +
                                        "  <price>31.95</price>\n" +
                                        "</book>\n" +
                                        "\n" +
                                        "</bookstore>", Body.Type.EXACT))
                ));
    }

    @Test
    public void clientCanCallServerNegativeMatchPathOnly() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
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
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_other_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_other_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_other_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_other_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchQueryStringParameterNameOnly() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("POST")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
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
        // wrong query string parameter name
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest" +
                                        "?OTHERQueryStringParameterOneName=queryStringParameterOneValueOne" +
                                        "&queryStringParameterOneName=queryStringParameterOneValueTwo" +
                                        "&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchBodyParameterNameOnly() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("POST")
                                .withPath("/some_pathRequest")
                                .withBody(new ParameterBody(
                                        new Parameter("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                                .withHeaders(new Header("headerNameResponse", "headerValueResponse"))
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // wrong query string parameter name
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new ParameterBody(
                                        new Parameter("OTHERBodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
        // wrong query string parameter name
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("OTHERBodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.EXACT))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchQueryStringParameterValueOnly() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("POST")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
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
        // wrong query string parameter value
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "OTHERqueryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchBodyParameterValueOnly() {
        // when
        mockServerClient
                .when(
                        new HttpRequest()
                                .withMethod("POST")
                                .withPath("/some_pathRequest")
                                .withBody(new ParameterBody(
                                        new Parameter("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                                .withHeaders(new Header("headerNameResponse", "headerValueResponse"))
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // wrong body parameter value
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new ParameterBody(
                                        new Parameter("bodyParameterOneName", "Other Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
        );
        // wrong body parameter value
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("bodyParameterOneName=Other Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.EXACT))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
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
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
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
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieOtherName", "cookieValue"))
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
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
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
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
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieOtherValue"))
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
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
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
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
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
                                .withHeaders(new Header("headerOtherName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
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
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
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
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
                                .withHeaders(new Header("headerName", "headerOtherValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(new StringBody("some_body", Body.Type.EXACT))
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
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2")
                ));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1")
                ));
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2")
                ));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1")
                ));
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
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1")
                ));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2")
                ));
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1")
                ));
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2")
                ));
    }

    protected HttpResponse makeRequest(HttpRequest httpRequest) {
        HttpResponse httpResponse = apacheHttpClient.sendRequest(httpRequest);
        List<Header> headers = new ArrayList<Header>();
        for (Header header : httpResponse.getHeaders()) {
            if (!(header.getName().equals("Server") || header.getName().equals("Expires") || header.getName().equals("Date") || header.getName().equals("Connection"))) {
                headers.add(header);
            }
        }
        httpResponse.withHeaders(headers);
        return httpResponse;
    }
}
