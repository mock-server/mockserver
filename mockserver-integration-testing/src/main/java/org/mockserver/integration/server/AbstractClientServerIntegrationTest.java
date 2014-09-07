package org.mockserver.integration.server;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockserver.configuration.SystemProperties.bufferSize;
import static org.mockserver.configuration.SystemProperties.maxTimeout;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.StringBody.*;

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
        apacheHttpClient = new ApacheHttpClient(true);
    }

    public abstract int getMockServerPort();

    public abstract int getMockServerSecurePort();

    public abstract int getTestServerPort();

    public abstract int getTestServerSecurePort();

    @Before
    public void resetServer() {
        mockServerClient.reset();
    }

    @Test
    public void clientCanCallServerForSimpleResponse() {
        // when
        mockServerClient.when(request()).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + ""),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "")),
                        false
                )
        );
    }

    @Test
    public void clientCanCallServerForForwardInHTTP() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath("/echo")
                )
                .forward(
                        forward()
                                .withHost("127.0.0.1")
                                .withPort(getTestServerPort())
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                new Header("host", "127.0.0.1:" + getTestServerPort()),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        new Header("X-Test", "test_headers_and_body"),
                                        new Header("Content-Type", "text/plain")
                                )
                                .withBody("an_example_body"),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                new Header("host", "127.0.0.1:" + getTestServerPort()),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        new Header("X-Test", "test_headers_and_body"),
                                        new Header("Host", "127.0.0.1:" + getTestServerPort()),
                                        new Header("Accept-Encoding", "gzip,deflate"),
                                        new Header("Content-Type", "text/plain")
                                )
                                .withBody("an_example_body"),
                        false
                )
        );
    }

    @Test
    public void clientCanCallServerForForwardInHTTPS() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath("/echo")
                )
                .forward(
                        forward()
                                .withHost("127.0.0.1")
                                .withPort(getTestServerSecurePort())
                                .withScheme(HttpForward.Scheme.HTTPS)
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                new Header("host", "127.0.0.1:" + getTestServerSecurePort()),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        new Header("X-Test", "test_headers_and_body"),
                                        new Header("Content-Type", "text/plain")
                                )
                                .withBody("an_example_body"),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                new Header("host", "127.0.0.1:" + getTestServerSecurePort()),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        new Header("X-Test", "test_headers_and_body"),
                                        new Header("Content-Type", "text/plain")
                                )
                                .withBody("an_example_body"),
                        false
                )
        );
    }

    @Test
    public void clientCanCallServerForResponseThenForward() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath("/echo"),
                        once()
                )
                .forward(
                        forward()
                                .withHost("127.0.0.1")
                                .withPort(getTestServerPort())
                );
        mockServerClient
                .when(
                        request()
                                .withPath("/test_headers_and_body"),
                        once()
                )
                .respond(
                        response()
                                .withBody("some_body")
                );

        // then
        // - forward
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                new Header("host", "127.0.0.1:" + getTestServerPort()),
                                new Header("accept-encoding", "gzip,deflate"),
                                new Header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        new Header("X-Test", "test_headers_and_body"),
                                        new Header("Content-Type", "text/plain")
                                )
                                .withBody("an_example_body"),
                        false
                )
        );
        // - respond
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "test_headers_and_body"),
                        false
                )
        );
        // - no response or forward
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "test_headers_and_body"),
                        false
                )
        );
    }

    @Test
    public void clientCanCallServerForResponseWithNoBody() {
        // when
        mockServerClient
                .when(
                        request().withMethod("POST").withPath("/some_path")
                )
                .respond(
                        response().withStatusCode(200)
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withMethod("POST"),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withMethod("POST"),
                        false
                )
        );
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2"),
                        false
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1"),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2"),
                        false
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1"),
                        false
                )
        );
    }

    @Test
    public void clientCanCallServerMatchPathXTimes() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path"), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path"),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path"),
                        false
                )
        );
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path"),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path"),
                        false
                )
        );
    }

    @Test
    public void clientCanVerifyRequestsReceived() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path"), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path"),
                        false
                )
        );
        mockServerClient.verify(new HttpRequest()
                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                .withPath("/some_path"));
        mockServerClient.verify(new HttpRequest()
                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                .withPath("/some_path"), org.mockserver.client.proxy.Times.exactly(1));

        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path"),
                        false
                )
        );
        mockServerClient.verify(new HttpRequest()
                .withURL("https{0,1}\\:\\/\\/localhost\\:\\d*\\/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("\\/") ? "\\/" : "") + "some_path")
                .withPath("/some_path"), org.mockserver.client.proxy.Times.atLeast(1));
        mockServerClient.verify(new HttpRequest()
                .withURL("https{0,1}\\:\\/\\/localhost\\:\\d*\\/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("\\/") ? "\\/" : "") + "some_path")
                .withPath("/some_path"), org.mockserver.client.proxy.Times.exactly(2));
    }

    @Test
    public void clientCanVerifyRequestsReceivedWithNoBody() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path"), exactly(2)).respond(new HttpResponse());

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path"),
                        false
                )
        );
        mockServerClient.verify(new HttpRequest()
                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                .withPath("/some_path"));
        mockServerClient.verify(new HttpRequest()
                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                .withPath("/some_path"), org.mockserver.client.proxy.Times.exactly(1));
    }

    @Test(expected = AssertionError.class)
    public void clientCanVerifyNotEnoughRequestsReceived() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path"), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path"),
                        false
                )
        );
        mockServerClient.verify(new HttpRequest()
                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                .withPath("/some_path"), org.mockserver.client.proxy.Times.atLeast(2));
    }

    @Test(expected = AssertionError.class)
    public void clientCanVerifyTooManyRequestsReceived() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path"), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path"),
                        false
                )
        );
        mockServerClient.verify(new HttpRequest()
                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                .withPath("/some_path"), org.mockserver.client.proxy.Times.exactly(0));
    }

    @Test(expected = AssertionError.class)
    public void clientCanVerifyNotMatchingRequestsReceived() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path"), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path"),
                        false
                )
        );
        mockServerClient.verify(new HttpRequest()
                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_other_path")
                .withPath("/some_other_path"), org.mockserver.client.proxy.Times.exactly(2));
    }

    @Test
    public void clientCanCallServerMatchBodyWithXPath() {
        // when
        mockServerClient.when(new HttpRequest().withBody(xpath("/bookstore/book[price>35]/price")), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withMethod("POST")
                                .withBody(new StringBody("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<bookstore>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<book category=\"COOKING\">" + System.getProperty("line.separator") +
                                        "  <title lang=\"en\">Everyday Italian</title>" + System.getProperty("line.separator") +
                                        "  <author>Giada De Laurentiis</author>" + System.getProperty("line.separator") +
                                        "  <year>2005</year>" + System.getProperty("line.separator") +
                                        "  <price>30.00</price>" + System.getProperty("line.separator") +
                                        "</book>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<book category=\"CHILDREN\">" + System.getProperty("line.separator") +
                                        "  <title lang=\"en\">Harry Potter</title>" + System.getProperty("line.separator") +
                                        "  <author>J K. Rowling</author>" + System.getProperty("line.separator") +
                                        "  <year>2005</year>" + System.getProperty("line.separator") +
                                        "  <price>29.99</price>" + System.getProperty("line.separator") +
                                        "</book>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<book category=\"WEB\">" + System.getProperty("line.separator") +
                                        "  <title lang=\"en\">Learning XML</title>" + System.getProperty("line.separator") +
                                        "  <author>Erik T. Ray</author>" + System.getProperty("line.separator") +
                                        "  <year>2003</year>" + System.getProperty("line.separator") +
                                        "  <price>39.95</price>" + System.getProperty("line.separator") +
                                        "</book>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "</bookstore>", Body.Type.STRING)),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withMethod("POST")
                                .withBody(new StringBody("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<bookstore>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<book category=\"COOKING\">" + System.getProperty("line.separator") +
                                        "  <title lang=\"en\">Everyday Italian</title>" + System.getProperty("line.separator") +
                                        "  <author>Giada De Laurentiis</author>" + System.getProperty("line.separator") +
                                        "  <year>2005</year>" + System.getProperty("line.separator") +
                                        "  <price>30.00</price>" + System.getProperty("line.separator") +
                                        "</book>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<book category=\"CHILDREN\">" + System.getProperty("line.separator") +
                                        "  <title lang=\"en\">Harry Potter</title>" + System.getProperty("line.separator") +
                                        "  <author>J K. Rowling</author>" + System.getProperty("line.separator") +
                                        "  <year>2005</year>" + System.getProperty("line.separator") +
                                        "  <price>29.99</price>" + System.getProperty("line.separator") +
                                        "</book>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<book category=\"WEB\">" + System.getProperty("line.separator") +
                                        "  <title lang=\"en\">Learning XML</title>" + System.getProperty("line.separator") +
                                        "  <author>Erik T. Ray</author>" + System.getProperty("line.separator") +
                                        "  <year>2003</year>" + System.getProperty("line.separator") +
                                        "  <price>39.95</price>" + System.getProperty("line.separator") +
                                        "</book>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "</bookstore>", Body.Type.STRING)),
                        false
                )
        );
    }

    @Test
    public void clientCanCallServerMatchBodyWithJson() {
        // when
        mockServerClient.when(new HttpRequest().withBody(json("{" + System.getProperty("line.separator") +
                "    \"GlossDiv\": {" + System.getProperty("line.separator") +
                "        \"title\": \"S\", " + System.getProperty("line.separator") +
                "        \"GlossList\": {" + System.getProperty("line.separator") +
                "            \"GlossEntry\": {" + System.getProperty("line.separator") +
                "                \"ID\": \"SGML\", " + System.getProperty("line.separator") +
                "                \"SortAs\": \"SGML\", " + System.getProperty("line.separator") +
                "                \"GlossTerm\": \"Standard Generalized Markup Language\", " + System.getProperty("line.separator") +
                "                \"Acronym\": \"SGML\", " + System.getProperty("line.separator") +
                "                \"Abbrev\": \"ISO 8879:1986\", " + System.getProperty("line.separator") +
                "                \"GlossDef\": {" + System.getProperty("line.separator") +
                "                    \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\", " + System.getProperty("line.separator") +
                "                    \"GlossSeeAlso\": [" + System.getProperty("line.separator") +
                "                        \"GML\", " + System.getProperty("line.separator") +
                "                        \"XML\"" + System.getProperty("line.separator") +
                "                    ]" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                \"GlossSee\": \"markup\"" + System.getProperty("line.separator") +
                "            }" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}")), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"title\": \"example glossary\", " + System.getProperty("line.separator") +
                                        "    \"GlossDiv\": {" + System.getProperty("line.separator") +
                                        "        \"title\": \"S\", " + System.getProperty("line.separator") +
                                        "        \"GlossList\": {" + System.getProperty("line.separator") +
                                        "            \"GlossEntry\": {" + System.getProperty("line.separator") +
                                        "                \"ID\": \"SGML\", " + System.getProperty("line.separator") +
                                        "                \"SortAs\": \"SGML\", " + System.getProperty("line.separator") +
                                        "                \"GlossTerm\": \"Standard Generalized Markup Language\", " + System.getProperty("line.separator") +
                                        "                \"Acronym\": \"SGML\", " + System.getProperty("line.separator") +
                                        "                \"Abbrev\": \"ISO 8879:1986\", " + System.getProperty("line.separator") +
                                        "                \"GlossDef\": {" + System.getProperty("line.separator") +
                                        "                    \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\", " + System.getProperty("line.separator") +
                                        "                    \"GlossSeeAlso\": [" + System.getProperty("line.separator") +
                                        "                        \"GML\", " + System.getProperty("line.separator") +
                                        "                        \"XML\"" + System.getProperty("line.separator") +
                                        "                    ]" + System.getProperty("line.separator") +
                                        "                }, " + System.getProperty("line.separator") +
                                        "                \"GlossSee\": \"markup\"" + System.getProperty("line.separator") +
                                        "            }" + System.getProperty("line.separator") +
                                        "        }" + System.getProperty("line.separator") +
                                        "    }" + System.getProperty("line.separator") +
                                        "}"),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"title\": \"example glossary\", " + System.getProperty("line.separator") +
                                        "    \"GlossDiv\": {" + System.getProperty("line.separator") +
                                        "        \"title\": \"S\", " + System.getProperty("line.separator") +
                                        "        \"GlossList\": {" + System.getProperty("line.separator") +
                                        "            \"GlossEntry\": {" + System.getProperty("line.separator") +
                                        "                \"ID\": \"SGML\", " + System.getProperty("line.separator") +
                                        "                \"SortAs\": \"SGML\", " + System.getProperty("line.separator") +
                                        "                \"GlossTerm\": \"Standard Generalized Markup Language\", " + System.getProperty("line.separator") +
                                        "                \"Acronym\": \"SGML\", " + System.getProperty("line.separator") +
                                        "                \"Abbrev\": \"ISO 8879:1986\", " + System.getProperty("line.separator") +
                                        "                \"GlossDef\": {" + System.getProperty("line.separator") +
                                        "                    \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\", " + System.getProperty("line.separator") +
                                        "                    \"GlossSeeAlso\": [" + System.getProperty("line.separator") +
                                        "                        \"GML\", " + System.getProperty("line.separator") +
                                        "                        \"XML\"" + System.getProperty("line.separator") +
                                        "                    ]" + System.getProperty("line.separator") +
                                        "                }, " + System.getProperty("line.separator") +
                                        "                \"GlossSee\": \"markup\"" + System.getProperty("line.separator") +
                                        "            }" + System.getProperty("line.separator") +
                                        "        }" + System.getProperty("line.separator") +
                                        "    }" + System.getProperty("line.separator") +
                                        "}"),
                        false
                )
        );
    }

    @Test
    public void clientCanSetupExpectationForPDF() throws IOException {
        // when
        byte[] pdfBytes = new byte[1024];
        IOUtils.readFully(getClass().getClassLoader().getResourceAsStream("test.pdf"), pdfBytes);
        mockServerClient
                .when(
                        request()
                                .withPath("/ws/rest/user/[0-9]+/document/[0-9]+\\.pdf")
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.OK_200.code())
                                .withHeaders(
                                        new Header(HttpHeaders.CONTENT_TYPE, MediaType.PDF.toString()),
                                        new Header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                        new Header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                                )
                                .withBody(binary(pdfBytes))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                new Header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                new Header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                        )
                        .withBody(binary(pdfBytes)),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "/ws/rest/user/1/document/2.pdf")
                                .withMethod("GET"),
                        true
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                new Header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                new Header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                        )
                        .withBody(binary(pdfBytes)),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "/ws/rest/user/1/document/2.pdf")
                                .withMethod("GET"),
                        true
                )
        );
    }

    @Test
    public void clientCanSetupExpectationForPNG() throws IOException {
        // when
        byte[] pngBytes = new byte[1024];
        IOUtils.readFully(getClass().getClassLoader().getResourceAsStream("test.png"), pngBytes);
        mockServerClient
                .when(
                        request()
                                .withPath("/ws/rest/user/[0-9]+/icon/[0-9]+\\.png")
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.OK_200.code())
                                .withHeaders(
                                        new Header(HttpHeaders.CONTENT_TYPE, MediaType.PNG.toString()),
                                        new Header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                                )
                                .withBody(binary(pngBytes))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                new Header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                        )
                        .withBody(binary(pngBytes)),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "/ws/rest/user/1/icon/1.png")
                                .withMethod("GET"),
                        true
                )
        );

        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                new Header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                        )
                        .withBody(binary(pngBytes)),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "/ws/rest/user/1/icon/1.png")
                                .withMethod("GET"),
                        true
                )
        );
    }

    @Test
    public void clientCanSetupExpectationForPDFAsBinaryBody() throws IOException {
        // when
        byte[] pdfBytes = new byte[1024];
        IOUtils.readFully(getClass().getClassLoader().getResourceAsStream("test.pdf"), pdfBytes);
        mockServerClient
                .when(
                        request().withBody(binary(pdfBytes))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.OK_200.code())
                                .withHeaders(
                                        new Header(HttpHeaders.CONTENT_TYPE, MediaType.PDF.toString()),
                                        new Header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                        new Header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                                )
                                .withBody(binary(pdfBytes))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                new Header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                new Header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                        )
                        .withBody(binary(pdfBytes)),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "ws/rest/user/1/document/2.pdf")
                                .withBody(binary(pdfBytes))
                                .withMethod("POST"),
                        true
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                new Header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                new Header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                        )
                        .withBody(binary(pdfBytes)),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "ws/rest/user/1/document/2.pdf")
                                .withBody(binary(pdfBytes))
                                .withMethod("POST"),
                        true
                )
        );
    }

    @Test
    public void clientCanSetupExpectationForPNGAsBinaryBody() throws IOException {
        // when
        byte[] pngBytes = new byte[1024];
        IOUtils.readFully(getClass().getClassLoader().getResourceAsStream("test.png"), pngBytes);
        mockServerClient
                .when(
                        request().withBody(binary(pngBytes))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.OK_200.code())
                                .withHeaders(
                                        new Header(HttpHeaders.CONTENT_TYPE, MediaType.PNG.toString()),
                                        new Header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                                )
                                .withBody(binary(pngBytes))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                new Header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                        )
                        .withBody(binary(pngBytes)),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "ws/rest/user/1/icon/1.png")
                                .withBody(binary(pngBytes))
                                .withMethod("POST"),
                        true
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                new Header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                        )
                        .withBody(binary(pngBytes)),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "ws/rest/user/1/icon/1.png")
                                .withBody(binary(pngBytes))
                                .withMethod("POST"),
                        true
                )
        );
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2"),
                        false
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1"),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2"),
                        false
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1"),
                        false
                )
        );
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        new Header("requestHeaderNameOne", "requestHeaderValueOne_One", "requestHeaderValueOne_Two"),
                                        new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                )
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        new Header("requestHeaderNameOne", "requestHeaderValueOne_One", "requestHeaderValueOne_Two"),
                                        new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                )
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        new Header("headerNameRequest", "headerValueRequest")
                                )
                                .withCookies(
                                        new Cookie("requestCookieNameOne", "requestCookieValueOne_One", "requestCookieValueOne_Two"),
                                        new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                ),
                        false
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        new Header("headerNameRequest", "headerValueRequest"),
                                        new Header("Cookie", "requestCookieNameOne=requestCookieValueOne_One; requestCookieNameOne=requestCookieValueOne_Two; requestCookieNameTwo=requestCookieValueTwo")
                                ),
                        false
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
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        new Header("headerNameRequest", "headerValueRequest")
                                )
                                .withCookies(
                                        new Cookie("requestCookieNameOne", "requestCookieValueOne_One", "requestCookieValueOne_Two"),
                                        new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                ),
                        false
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
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        new Header("headerNameRequest", "headerValueRequest"),
                                        new Header("Cookie", "requestCookieNameOne=requestCookieValueOne_One; requestCookieNameOne=requestCookieValueOne_Two; requestCookieNameTwo=requestCookieValueTwo")
                                ),
                        false
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path" +
                                        "?queryStringParameterOneName=queryStringParameterOneValueOne" +
                                        "&queryStringParameterOneName=queryStringParameterOneValueTwo" +
                                        "&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withBody(params(new Parameter("bodyParameterName", "bodyParameterValue"))),
                        false
                )
        );
        // - in https - query string parameter objects
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(params(new Parameter("bodyParameterName=bodyParameterValue"))),
                        false
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest" +
                                        "?queryStringParameterOneName=queryStringParameterOneValueOne" +
                                        "&queryStringParameterOneName=queryStringParameterOneValueTwo" +
                                        "&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
                )
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest" +
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
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
                )
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
                                .withBody(params(
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.STRING))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(params(
                                        new Parameter("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest" +
                                        "?bodyParameterOneName=bodyParameterOneValueOne" +
                                        "&bodyParameterOneName=bodyParameterOneValueTwo" +
                                        "&bodyParameterTwoName=bodyParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withBody(params(
                                        new Parameter("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.STRING))
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.STRING))
                                .withHeaders(
                                        new Header("headerNameRequest", "headerValueRequest"),
                                        new Header("Cookie", "cookieNameRequest=cookieValueRequest")
                                ),
                        false
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(params(
                                        new Parameter("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withBody(exact("some_body"))
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_other_body"))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_other_body"))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        false
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchXPathBodyOnly() {
        // when
        mockServerClient.when(new HttpRequest().withBody(new StringBody("/bookstore/book[price>35]/price", Body.Type.XPATH)), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withMethod("POST")
                                .withBody(new StringBody("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<bookstore>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<book category=\"COOKING\">" + System.getProperty("line.separator") +
                                        "  <title lang=\"en\">Everyday Italian</title>" + System.getProperty("line.separator") +
                                        "  <author>Giada De Laurentiis</author>" + System.getProperty("line.separator") +
                                        "  <year>2005</year>" + System.getProperty("line.separator") +
                                        "  <price>30.00</price>" + System.getProperty("line.separator") +
                                        "</book>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<book category=\"CHILDREN\">" + System.getProperty("line.separator") +
                                        "  <title lang=\"en\">Harry Potter</title>" + System.getProperty("line.separator") +
                                        "  <author>J K. Rowling</author>" + System.getProperty("line.separator") +
                                        "  <year>2005</year>" + System.getProperty("line.separator") +
                                        "  <price>29.99</price>" + System.getProperty("line.separator") +
                                        "</book>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<book category=\"WEB\">" + System.getProperty("line.separator") +
                                        "  <title lang=\"en\">Learning XML</title>" + System.getProperty("line.separator") +
                                        "  <author>Erik T. Ray</author>" + System.getProperty("line.separator") +
                                        "  <year>2003</year>" + System.getProperty("line.separator") +
                                        "  <price>31.95</price>" + System.getProperty("line.separator") +
                                        "</book>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "</bookstore>", Body.Type.STRING)),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withMethod("POST")
                                .withBody(new StringBody("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<bookstore>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<book category=\"COOKING\">" + System.getProperty("line.separator") +
                                        "  <title lang=\"en\">Everyday Italian</title>" + System.getProperty("line.separator") +
                                        "  <author>Giada De Laurentiis</author>" + System.getProperty("line.separator") +
                                        "  <year>2005</year>" + System.getProperty("line.separator") +
                                        "  <price>30.00</price>" + System.getProperty("line.separator") +
                                        "</book>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<book category=\"CHILDREN\">" + System.getProperty("line.separator") +
                                        "  <title lang=\"en\">Harry Potter</title>" + System.getProperty("line.separator") +
                                        "  <author>J K. Rowling</author>" + System.getProperty("line.separator") +
                                        "  <year>2005</year>" + System.getProperty("line.separator") +
                                        "  <price>29.99</price>" + System.getProperty("line.separator") +
                                        "</book>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "<book category=\"WEB\">" + System.getProperty("line.separator") +
                                        "  <title lang=\"en\">Learning XML</title>" + System.getProperty("line.separator") +
                                        "  <author>Erik T. Ray</author>" + System.getProperty("line.separator") +
                                        "  <year>2003</year>" + System.getProperty("line.separator") +
                                        "  <price>31.95</price>" + System.getProperty("line.separator") +
                                        "</book>" + System.getProperty("line.separator") +
                                        "" + System.getProperty("line.separator") +
                                        "</bookstore>", Body.Type.STRING)),
                        false
                )
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchJsonBodyOnly() {
        // when
        mockServerClient.when(new HttpRequest().withBody(json("{" + System.getProperty("line.separator") +
                "    \"title\": \"example glossary\", " + System.getProperty("line.separator") +
                "    \"GlossDiv\": {" + System.getProperty("line.separator") +
                "        \"title\": \"wrong_value\", " + System.getProperty("line.separator") +
                "        \"GlossList\": {" + System.getProperty("line.separator") +
                "            \"GlossEntry\": {" + System.getProperty("line.separator") +
                "                \"ID\": \"SGML\", " + System.getProperty("line.separator") +
                "                \"SortAs\": \"SGML\", " + System.getProperty("line.separator") +
                "                \"GlossTerm\": \"Standard Generalized Markup Language\", " + System.getProperty("line.separator") +
                "                \"Acronym\": \"SGML\", " + System.getProperty("line.separator") +
                "                \"Abbrev\": \"ISO 8879:1986\", " + System.getProperty("line.separator") +
                "                \"GlossDef\": {" + System.getProperty("line.separator") +
                "                    \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\", " + System.getProperty("line.separator") +
                "                    \"GlossSeeAlso\": [" + System.getProperty("line.separator") +
                "                        \"GML\", " + System.getProperty("line.separator") +
                "                        \"XML\"" + System.getProperty("line.separator") +
                "                    ]" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                \"GlossSee\": \"markup\"" + System.getProperty("line.separator") +
                "            }" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}")), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"title\": \"example glossary\", " + System.getProperty("line.separator") +
                                        "    \"GlossDiv\": {" + System.getProperty("line.separator") +
                                        "        \"title\": \"S\", " + System.getProperty("line.separator") +
                                        "        \"GlossList\": {" + System.getProperty("line.separator") +
                                        "            \"GlossEntry\": {" + System.getProperty("line.separator") +
                                        "                \"ID\": \"SGML\", " + System.getProperty("line.separator") +
                                        "                \"SortAs\": \"SGML\", " + System.getProperty("line.separator") +
                                        "                \"GlossTerm\": \"Standard Generalized Markup Language\", " + System.getProperty("line.separator") +
                                        "                \"Acronym\": \"SGML\", " + System.getProperty("line.separator") +
                                        "                \"Abbrev\": \"ISO 8879:1986\", " + System.getProperty("line.separator") +
                                        "                \"GlossDef\": {" + System.getProperty("line.separator") +
                                        "                    \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\", " + System.getProperty("line.separator") +
                                        "                    \"GlossSeeAlso\": [" + System.getProperty("line.separator") +
                                        "                        \"GML\", " + System.getProperty("line.separator") +
                                        "                        \"XML\"" + System.getProperty("line.separator") +
                                        "                    ]" + System.getProperty("line.separator") +
                                        "                }, " + System.getProperty("line.separator") +
                                        "                \"GlossSee\": \"markup\"" + System.getProperty("line.separator") +
                                        "            }" + System.getProperty("line.separator") +
                                        "        }" + System.getProperty("line.separator") +
                                        "    }" + System.getProperty("line.separator") +
                                        "}"),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path")
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"title\": \"example glossary\", " + System.getProperty("line.separator") +
                                        "    \"GlossDiv\": {" + System.getProperty("line.separator") +
                                        "        \"title\": \"S\", " + System.getProperty("line.separator") +
                                        "        \"GlossList\": {" + System.getProperty("line.separator") +
                                        "            \"GlossEntry\": {" + System.getProperty("line.separator") +
                                        "                \"ID\": \"SGML\", " + System.getProperty("line.separator") +
                                        "                \"SortAs\": \"SGML\", " + System.getProperty("line.separator") +
                                        "                \"GlossTerm\": \"Standard Generalized Markup Language\", " + System.getProperty("line.separator") +
                                        "                \"Acronym\": \"SGML\", " + System.getProperty("line.separator") +
                                        "                \"Abbrev\": \"ISO 8879:1986\", " + System.getProperty("line.separator") +
                                        "                \"GlossDef\": {" + System.getProperty("line.separator") +
                                        "                    \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\", " + System.getProperty("line.separator") +
                                        "                    \"GlossSeeAlso\": [" + System.getProperty("line.separator") +
                                        "                        \"GML\", " + System.getProperty("line.separator") +
                                        "                        \"XML\"" + System.getProperty("line.separator") +
                                        "                    ]" + System.getProperty("line.separator") +
                                        "                }, " + System.getProperty("line.separator") +
                                        "                \"GlossSee\": \"markup\"" + System.getProperty("line.separator") +
                                        "            }" + System.getProperty("line.separator") +
                                        "        }" + System.getProperty("line.separator") +
                                        "    }" + System.getProperty("line.separator") +
                                        "}"),
                        false
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
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_other_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_other_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_other_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_other_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        false
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest" +
                                        "?OTHERQueryStringParameterOneName=queryStringParameterOneValueOne" +
                                        "&queryStringParameterOneName=queryStringParameterOneValueTwo" +
                                        "&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withBody(params(
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(params(
                                        new Parameter("OTHERBodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
                )
        );
        // wrong query string parameter name
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("OTHERBodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.STRING))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "OTHERqueryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withBody(params(
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(params(
                                        new Parameter("bodyParameterOneName", "Other Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
                )
        );
        // wrong body parameter value
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("POST")
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("bodyParameterOneName=Other Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.STRING))
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        false
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
                                .withBody(exact("some_body"))
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieOtherName", "cookieValue")),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieOtherName", "cookieValue")),
                        false
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
                                .withBody(exact("some_body"))
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieOtherValue")),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieOtherValue")),
                        false
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
                                .withBody(exact("some_body"))
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(new Header("headerOtherName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(new Header("headerOtherName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        false
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
                                .withBody(exact("some_body"))
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(new Header("headerName", "headerOtherValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withMethod("GET")
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(new Header("headerName", "headerOtherValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        false
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2"),
                        false
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1"),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2"),
                        false
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1"),
                        false
                )
        );
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1"),
                        false
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2"),
                        false
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path1")
                                .withPath("/some_path1"),
                        false
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path2")
                                .withPath("/some_path2"),
                        false
                )
        );
    }

    protected HttpResponse makeRequest(HttpRequest httpRequest, boolean binaryBody) {
        HttpResponse httpResponse = apacheHttpClient.sendRequest(httpRequest, binaryBody);
        List<Header> headers = new ArrayList<Header>();
        for (Header header : httpResponse.getHeaders()) {
            if (!(header.getName().equalsIgnoreCase("Server") || header.getName().equalsIgnoreCase("Expires") || header.getName().equalsIgnoreCase("Date") || header.getName().equalsIgnoreCase("Connection") || header.getName().equalsIgnoreCase("User-Agent") || header.getName().equalsIgnoreCase("Content-Type"))) {
                headers.add(header);
            }
        }
        httpResponse.withHeaders(headers);
        return httpResponse;
    }
}
