package org.mockserver.integration.server;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.netty.SocketConnectionException;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatchType;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.socket.PortFactory;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.matchers.Times.unlimited;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.RegexBody.regex;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.XPathBody.xpath;
import static org.mockserver.model.XmlBody.xml;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientServerIntegrationTest {

    protected static final String TEXT_PLAIN = MediaType.create("text", "plain").toString();
    protected static MockServerClient mockServerClient;
    protected static String servletContext = "";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    protected List<String> headersToIgnore = Arrays.asList(
            "server",
            "expires",
            "date",
            "host",
            "connection",
            "user-agent",
            "content-length",
            "accept-encoding",
            "transfer-encoding",
            "access-control-allow-origin",
            "access-control-allow-methods",
            "access-control-allow-headers",
            "access-control-expose-headers",
            "access-control-max-age",
            "x-cors"
    );
    private NettyHttpClient httpClient = new NettyHttpClient();

    public static Expectation expectation(HttpRequest httpRequest) {
        return new Expectation(httpRequest, Times.unlimited(), TimeToLive.unlimited());
    }

    public abstract int getMockServerPort();

    public abstract int getMockServerSecurePort();

    public abstract int getTestServerPort();

    @BeforeClass
    public static void resetServletContext() throws Exception {
        servletContext = "";
    }

    @Before
    public void resetServer() {
        mockServerClient.reset();
    }

    @Test
    public void shouldForwardRequestInHTTP() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("echo"))
                )
                .forward(
                        forward()
                                .withHost("127.0.0.1")
                                .withPort(getTestServerPort())
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withHeaders(
                                header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_http"),
                makeRequest(
                        request()
                                .withPath(calculatePath("echo"))
                                .withMethod("POST")
                                .withHeaders(
                                        header("x-test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_http"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withHeaders(
                                header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_https"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("echo"))
                                .withMethod("POST")
                                .withHeaders(
                                        header("x-test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_https"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldForwardRequestInHTTPS() {
        int testServerHttpsPort = PortFactory.findFreePort();
        EchoServer secureEchoServer = new EchoServer(testServerHttpsPort, true);
        try {
            // when
            mockServerClient
                    .when(
                            request()
                                    .withPath(calculatePath("echo"))
                    )
                    .forward(
                            forward()
                                    .withHost("127.0.0.1")
                                    .withPort(testServerHttpsPort)
                                    .withScheme(HttpForward.Scheme.HTTPS)
                    );

            // then
            // - in http
            assertEquals(
                    response()
                            .withStatusCode(OK_200.code())
                            .withHeaders(
                                    header("x-test", "test_headers_and_body")
                            )
                            .withBody("an_example_body_http"),
                    makeRequest(
                            request()
                                    .withPath(calculatePath("echo"))
                                    .withMethod("POST")
                                    .withHeaders(
                                            header("x-test", "test_headers_and_body")
                                    )
                                    .withBody("an_example_body_http"),
                            headersToIgnore)
            );
            // - in https
            assertEquals(
                    response()
                            .withStatusCode(OK_200.code())
                            .withHeaders(
                                    header("x-test", "test_headers_and_body")
                            )
                            .withBody("an_example_body_https"),
                    makeRequest(
                            request()
                                    .withSecure(true)
                                    .withPath(calculatePath("echo"))
                                    .withMethod("POST")
                                    .withHeaders(
                                            header("x-test", "test_headers_and_body")
                                    )
                                    .withBody("an_example_body_https"),
                            headersToIgnore)
            );
        } finally {
            secureEchoServer.stop();
        }
    }

    @Test
    public void shouldAllowSimultaneousForwardAndResponseExpectations() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("echo")),
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
                                .withPath(calculatePath("test_headers_and_body")),
                        once()
                )
                .respond(
                        response()
                                .withBody("some_body")
                );

        // then
        // - forward
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withHeaders(
                                header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("echo"))
                                .withMethod("POST")
                                .withHeaders(
                                        header("x-test", "test_headers_and_body")
                                )
                                .withBody("an_example_body"),
                        headersToIgnore)
        );
        // - respond
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("test_headers_and_body")),
                        headersToIgnore)
        );
        // - no response or forward
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("test_headers_and_body")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldCallbackToSpecifiedClassWithPrecannedResponse() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("callback"))
                )
                .callback(
                        callback()
                                .withCallbackClass("org.mockserver.integration.callback.PrecannedTestExpectationCallback")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withHeaders(
                                header("x-callback", "test_callback_header")
                        )
                        .withBody("a_callback_response"),
                makeRequest(
                        request()
                                .withPath(calculatePath("callback"))
                                .withMethod("POST")
                                .withHeaders(
                                        header("x-test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_http"),
                        headersToIgnore
                )
        );

        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withHeaders(
                                header("x-callback", "test_callback_header")
                        )
                        .withBody("a_callback_response"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("callback"))
                                .withMethod("POST")
                                .withHeaders(
                                        header("x-test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_https"),
                        headersToIgnore
                )
        );
    }

    @Test
    public void shouldReturnResponseWithOnlyBody() {
        // when
        mockServerClient.when(request()).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldSupportBatchedExpectations() {
        // when
        new NettyHttpClient().sendRequest(
                request()
                        .withMethod("PUT")
                        .withHeader(HOST.toString(), "localhost:" + getMockServerPort())
                        .withPath(addContextToPath("/expectation"))
                        .withBody("" +
                                "[" +
                                new ExpectationSerializer()
                                        .serialize(
                                                new Expectation(request("/path_one"), once(), TimeToLive.unlimited())
                                                        .thenRespond(response().withBody("some_body_one"))
                                        ) + "," +
                                new ExpectationSerializer()
                                        .serialize(
                                                new Expectation(request("/path_two"), once(), TimeToLive.unlimited())
                                                        .thenRespond(response().withBody("some_body_two"))
                                        ) + "," +
                                new ExpectationSerializer()
                                        .serialize(
                                                new Expectation(request("/path_three"), once(), TimeToLive.unlimited())
                                                        .thenRespond(response().withBody("some_body_three"))
                                        ) +
                                "]"
                        )
        );

        // then
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body_one"),
                makeRequest(
                        request()
                                .withPath(calculatePath("/path_one")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body_two"),
                makeRequest(
                        request()
                                .withPath(calculatePath("/path_two")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body_three"),
                makeRequest(
                        request()
                                .withPath(calculatePath("/path_three")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseForRequestInSsl() {
        // when
        mockServerClient.when(request().withSecure(true)).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseForRequestNotInSsl() {
        // when
        mockServerClient.when(request().withSecure(false)).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnMatchRequestWithBodyInUTF16() {
        // when
        String body = "我说中国话";
        mockServerClient.when(request().withBody(body, Charsets.UTF_16)).respond(response().withBody(body, Charsets.UTF_8));

        // then
        // - in http
        assertEquals(
                response()
                        .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(Charsets.UTF_8).toString())
                        .withStatusCode(OK_200.code())
                        .withBody(body),
                makeRequest(
                        request()
                                .withPath(calculatePath(""))
                                .withBody(body, Charsets.UTF_16),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(Charsets.UTF_8).toString())
                        .withStatusCode(OK_200.code())
                        .withBody(body),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath(""))
                                .withBody(body, Charsets.UTF_16),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnMatchRequestWithBodyInUTF16WithContentTypeHeader() {
        // when
        String body = "我说中国话";
        mockServerClient
                .when(
                        request()
                                .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(Charsets.UTF_8).toString())
                                .withBody(body)
                )
                .respond(
                        response()
                                .withBody(body, Charsets.UTF_8)
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(Charsets.UTF_8).toString())
                        .withStatusCode(OK_200.code())
                        .withBody(body),
                makeRequest(
                        request()
                                .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(Charsets.UTF_8).toString())
                                .withPath(calculatePath(""))
                                .withBody(body),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(Charsets.UTF_8).toString())
                        .withStatusCode(OK_200.code())
                        .withBody(body),
                makeRequest(
                        request()
                                .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(Charsets.UTF_8).toString())
                                .withSecure(true)
                                .withPath(calculatePath(""))
                                .withBody(body),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseWithBodyInUTF8() {
        // when
        String body = "我说中国话";
        mockServerClient.when(request()).respond(response().withBody(body, Charsets.UTF_16));

        // then
        // - in http
        assertEquals(
                response()
                        .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(Charsets.UTF_16).toString())
                        .withStatusCode(OK_200.code())
                        .withBody(body),
                makeRequest(
                        request()
                                .withPath(calculatePath("")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(Charsets.UTF_16).toString())
                        .withStatusCode(OK_200.code())
                        .withBody(body),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseWithBodyInUTF16WithContentTypeHeader() {
        // when
        String body = "我说中国话";
        mockServerClient
                .when(
                        request()
                )
                .respond(
                        response()
                                .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(Charsets.UTF_8).toString())
                                .withBody(body)
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(Charsets.UTF_8).toString())
                        .withStatusCode(OK_200.code())
                        .withBody(body),
                makeRequest(
                        request()
                                .withPath(calculatePath("")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(Charsets.UTF_8).toString())
                        .withStatusCode(OK_200.code())
                        .withBody(body),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseWithOnlyStatusCode() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_path"))
                )
                .respond(
                        response()
                                .withStatusCode(200)
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPath() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path1"))
                )
                .respond(
                        response()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path2"))
                )
                .respond(
                        response()
                                .withBody("some_body2")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path1")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path1")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathExactTimes() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path")), exactly(2)
                )
                .respond(
                        response()
                                .withBody("some_body")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseWhenTimeToLiveHasNotExpired() {
        // when
        mockServerClient
                .when(
                        request().withPath(calculatePath("some_path")),
                        exactly(2),
                        TimeToLive.exactly(TimeUnit.HOURS, 1L)
                )
                .respond(
                        response().withBody("some_body")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithXPath() {
        // when
        mockServerClient.when(request().withBody(xpath("/bookstore/book[price>30]/price")), exactly(2)).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody(new StringBody("" +
                                        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + System.getProperty("line.separator") +
                                        "<bookstore>" + System.getProperty("line.separator") +
                                        "  <book category=\"COOKING\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Everyday Italian</title>" + System.getProperty("line.separator") +
                                        "    <author>Giada De Laurentiis</author>" + System.getProperty("line.separator") +
                                        "    <year>2005</year>" + System.getProperty("line.separator") +
                                        "    <price>30.00</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "  <book category=\"CHILDREN\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Harry Potter</title>" + System.getProperty("line.separator") +
                                        "    <author>J K. Rowling</author>" + System.getProperty("line.separator") +
                                        "    <year>2005</year>" + System.getProperty("line.separator") +
                                        "    <price>29.99</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "  <book category=\"WEB\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Learning XML</title>" + System.getProperty("line.separator") +
                                        "    <author>Erik T. Ray</author>" + System.getProperty("line.separator") +
                                        "    <year>2003</year>" + System.getProperty("line.separator") +
                                        "    <price>31.95</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "</bookstore>")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody(new StringBody("" +
                                        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + System.getProperty("line.separator") +
                                        "<bookstore>" + System.getProperty("line.separator") +
                                        "  <book category=\"COOKING\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Everyday Italian</title>" + System.getProperty("line.separator") +
                                        "    <author>Giada De Laurentiis</author>" + System.getProperty("line.separator") +
                                        "    <year>2005</year>" + System.getProperty("line.separator") +
                                        "    <price>30.00</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "  <book category=\"CHILDREN\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Harry Potter</title>" + System.getProperty("line.separator") +
                                        "    <author>J K. Rowling</author>" + System.getProperty("line.separator") +
                                        "    <year>2005</year>" + System.getProperty("line.separator") +
                                        "    <price>29.99</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "  <book category=\"WEB\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Learning XML</title>" + System.getProperty("line.separator") +
                                        "    <author>Erik T. Ray</author>" + System.getProperty("line.separator") +
                                        "    <year>2003</year>" + System.getProperty("line.separator") +
                                        "    <price>31.95</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "</bookstore>")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithXml() {
        // when
        mockServerClient.when(request().withBody(xml("" +
                "<bookstore>" + System.getProperty("line.separator") +
                "  <book nationality=\"ITALIAN\" category=\"COOKING\"><title lang=\"en\">Everyday Italian</title><author>Giada De Laurentiis</author><year>2005</year><price>30.00</price></book>" + System.getProperty("line.separator") +
                "</bookstore>")), exactly(2)).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody(new StringBody("" +
                                        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + System.getProperty("line.separator") +
                                        "<bookstore>" + System.getProperty("line.separator") +
                                        "  <book category=\"COOKING\" nationality=\"ITALIAN\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Everyday Italian</title>" + System.getProperty("line.separator") +
                                        "    <author>Giada De Laurentiis</author>" + System.getProperty("line.separator") +
                                        "    <year>2005</year>" + System.getProperty("line.separator") +
                                        "    <price>30.00</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "</bookstore>")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody(new StringBody("" +
                                        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + System.getProperty("line.separator") +
                                        "<bookstore>" + System.getProperty("line.separator") +
                                        "  <book category=\"COOKING\" nationality=\"ITALIAN\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Everyday Italian</title>" + System.getProperty("line.separator") +
                                        "    <author>Giada De Laurentiis</author>" + System.getProperty("line.separator") +
                                        "    <year>2005</year>" + System.getProperty("line.separator") +
                                        "    <price>30.00</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "</bookstore>")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJson() {
        // when
        mockServerClient
                .when(
                        request()
                                .withBody(json("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}")),
                        exactly(2)
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"extra ignored field\": \"some value\"," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"extra ignored array\": [\"one\", \"two\"]," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJsonWithCharsetUTF8() {
        // when
        mockServerClient
                .when(
                        request()
                                .withBody(json("{" + System.getProperty("line.separator") +
                                        "    \"ταυτότητα\": 1," + System.getProperty("line.separator") +
                                        "    \"όνομα\": \"μια πράσινη πόρτα\"," + System.getProperty("line.separator") +
                                        "    \"τιμή\": 12.50," + System.getProperty("line.separator") +
                                        "    \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + System.getProperty("line.separator") +
                                        "}", Charsets.UTF_16, MatchType.ONLY_MATCHING_FIELDS)),
                        exactly(2)
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody(json("{" + System.getProperty("line.separator") +
                                        "    \"ταυτότητα\": 1," + System.getProperty("line.separator") +
                                        "    \"επιπλέον αγνοούνται τομέα\": \"κάποια αξία\"," + System.getProperty("line.separator") +
                                        "    \"όνομα\": \"μια πράσινη πόρτα\"," + System.getProperty("line.separator") +
                                        "    \"τιμή\": 12.50," + System.getProperty("line.separator") +
                                        "    \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + System.getProperty("line.separator") +
                                        "}", Charsets.UTF_16, MatchType.ONLY_MATCHING_FIELDS)),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody(json("{" + System.getProperty("line.separator") +
                                        "    \"ταυτότητα\": 1," + System.getProperty("line.separator") +
                                        "    \"επιπλέον αγνοούνται σειρά\": [\"ένας\", \"δυο\"]," + System.getProperty("line.separator") +
                                        "    \"όνομα\": \"μια πράσινη πόρτα\"," + System.getProperty("line.separator") +
                                        "    \"τιμή\": 12.50," + System.getProperty("line.separator") +
                                        "    \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + System.getProperty("line.separator") +
                                        "}", Charsets.UTF_16, MatchType.ONLY_MATCHING_FIELDS)),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJsonWithContentTypeHeader() {
        // when
        mockServerClient
                .when(
                        request()
                                .withBody(json("{" + System.getProperty("line.separator") +
                                        "    \"ταυτότητα\": 1," + System.getProperty("line.separator") +
                                        "    \"όνομα\": \"μια πράσινη πόρτα\"," + System.getProperty("line.separator") +
                                        "    \"τιμή\": 12.50," + System.getProperty("line.separator") +
                                        "    \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + System.getProperty("line.separator") +
                                        "}", Charsets.UTF_16, MatchType.ONLY_MATCHING_FIELDS)),
                        exactly(2)
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(Charsets.UTF_16).toString())
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody(json("{" + System.getProperty("line.separator") +
                                        "    \"ταυτότητα\": 1," + System.getProperty("line.separator") +
                                        "    \"επιπλέον αγνοούνται τομέα\": \"κάποια αξία\"," + System.getProperty("line.separator") +
                                        "    \"όνομα\": \"μια πράσινη πόρτα\"," + System.getProperty("line.separator") +
                                        "    \"τιμή\": 12.50," + System.getProperty("line.separator") +
                                        "    \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + System.getProperty("line.separator") +
                                        "}")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withHeader(CONTENT_TYPE.toString(), MediaType.create("text", "plain").withCharset(Charsets.UTF_16).toString())
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody(json("{" + System.getProperty("line.separator") +
                                        "    \"ταυτότητα\": 1," + System.getProperty("line.separator") +
                                        "    \"επιπλέον αγνοούνται σειρά\": [\"ένας\", \"δυο\"]," + System.getProperty("line.separator") +
                                        "    \"όνομα\": \"μια πράσινη πόρτα\"," + System.getProperty("line.separator") +
                                        "    \"τιμή\": 12.50," + System.getProperty("line.separator") +
                                        "    \"ετικέτες\": [\"σπίτι\", \"πράσινος\"]" + System.getProperty("line.separator") +
                                        "}")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJsonWithMatchType() {
        // when
        mockServerClient
                .when(
                        request()
                                .withBody(json("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}", MatchType.ONLY_MATCHING_FIELDS)),
                        exactly(2)
                )
                .respond(
                        response()
                                .withBody("some_body")
                );

        // then
        // - in http
        assertEquals(
                response("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"extra field\": \"some value\"," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response("some_body"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"extra field\": \"some value\"," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingBodyWithJsonSchema() {
        // when
        mockServerClient.when(request().withBody(jsonSchema("{" + System.getProperty("line.separator") +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + System.getProperty("line.separator") +
                "    \"title\": \"Product\"," + System.getProperty("line.separator") +
                "    \"description\": \"A product from Acme's catalog\"," + System.getProperty("line.separator") +
                "    \"type\": \"object\"," + System.getProperty("line.separator") +
                "    \"properties\": {" + System.getProperty("line.separator") +
                "        \"id\": {" + System.getProperty("line.separator") +
                "            \"description\": \"The unique identifier for a product\"," + System.getProperty("line.separator") +
                "            \"type\": \"integer\"" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"name\": {" + System.getProperty("line.separator") +
                "            \"description\": \"Name of the product\"," + System.getProperty("line.separator") +
                "            \"type\": \"string\"" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"price\": {" + System.getProperty("line.separator") +
                "            \"type\": \"number\"," + System.getProperty("line.separator") +
                "            \"minimum\": 0," + System.getProperty("line.separator") +
                "            \"exclusiveMinimum\": true" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"tags\": {" + System.getProperty("line.separator") +
                "            \"type\": \"array\"," + System.getProperty("line.separator") +
                "            \"items\": {" + System.getProperty("line.separator") +
                "                \"type\": \"string\"" + System.getProperty("line.separator") +
                "            }," + System.getProperty("line.separator") +
                "            \"minItems\": 1," + System.getProperty("line.separator") +
                "            \"uniqueItems\": true" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"required\": [\"id\", \"name\", \"price\"]" + System.getProperty("line.separator") +
                "}")), exactly(2)).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnPDFResponseByMatchingPath() throws IOException {
        // when
        byte[] pdfBytes = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("test.pdf"));
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("ws/rest/user/[0-9]+/document/[0-9]+\\.pdf"))
                )
                .respond(
                        response()
                                .withStatusCode(OK_200.code())
                                .withHeaders(
                                        header(CONTENT_TYPE.toString(), MediaType.PDF.toString()),
                                        header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                        header(CACHE_CONTROL.toString(), "must-revalidate, post-check=0, pre-check=0")
                                )
                                .withBody(binary(pdfBytes))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withHeaders(
                                header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                header(CACHE_CONTROL.toString(), "must-revalidate, post-check=0, pre-check=0"),
                                header(CONTENT_TYPE.toString(), MediaType.PDF.toString())
                        )
                        .withBody(binary(pdfBytes)),
                makeRequest(
                        request()
                                .withPath(calculatePath("ws/rest/user/1/document/2.pdf"))
                                .withMethod("GET"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withHeaders(
                                header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                header(CACHE_CONTROL.toString(), "must-revalidate, post-check=0, pre-check=0"),
                                header(CONTENT_TYPE.toString(), MediaType.PDF.toString())
                        )
                        .withBody(binary(pdfBytes)),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("ws/rest/user/1/document/2.pdf"))
                                .withMethod("GET"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnPNGResponseByMatchingPath() throws IOException {
        // when
        byte[] pngBytes = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("test.png"));
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("ws/rest/user/[0-9]+/icon/[0-9]+\\.png"))
                )
                .respond(
                        response()
                                .withStatusCode(OK_200.code())
                                .withHeaders(
                                        header(CONTENT_TYPE.toString(), MediaType.PNG.toString()),
                                        header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.png\"; filename=\"test.png\"")
                                )
                                .withBody(binary(pngBytes))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withHeaders(
                                header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.png\"; filename=\"test.png\""),
                                header(CONTENT_TYPE.toString(), MediaType.PNG.toString())
                        )
                        .withBody(binary(pngBytes)),
                makeRequest(
                        request()
                                .withPath(calculatePath("ws/rest/user/1/icon/1.png"))
                                .withMethod("GET"),
                        headersToIgnore)
        );

        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withHeaders(
                                header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.png\"; filename=\"test.png\""),
                                header(CONTENT_TYPE.toString(), MediaType.PNG.toString())
                        )
                        .withBody(binary(pngBytes)),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("ws/rest/user/1/icon/1.png"))
                                .withMethod("GET"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnPDFResponseByMatchingBinaryPDFBody() throws IOException {
        // when
        byte[] pdfBytes = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("test.pdf"));
        mockServerClient
                .when(
                        request().withBody(binary(pdfBytes))
                )
                .respond(
                        response()
                                .withStatusCode(OK_200.code())
                                .withHeaders(
                                        header(CONTENT_TYPE.toString(), MediaType.PDF.toString()),
                                        header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                        header(CACHE_CONTROL.toString(), "must-revalidate, post-check=0, pre-check=0")
                                )
                                .withBody(binary(pdfBytes))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withHeaders(
                                header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                header(CACHE_CONTROL.toString(), "must-revalidate, post-check=0, pre-check=0"),
                                header(CONTENT_TYPE.toString(), MediaType.PDF.toString())
                        )
                        .withBody(binary(pdfBytes)),
                makeRequest(
                        request()
                                .withPath(calculatePath("ws/rest/user/1/document/2.pdf"))
                                .withBody(binary(pdfBytes))
                                .withMethod("POST"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withHeaders(
                                header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                header(CACHE_CONTROL.toString(), "must-revalidate, post-check=0, pre-check=0"),
                                header(CONTENT_TYPE.toString(), MediaType.PDF.toString())
                        )
                        .withBody(binary(pdfBytes)),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("ws/rest/user/1/document/2.pdf"))
                                .withBody(binary(pdfBytes))
                                .withMethod("POST"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnPNGResponseByMatchingBinaryPNGBody() throws IOException {
        // when
        byte[] pngBytes = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("test.png"));
        mockServerClient
                .when(
                        request().withBody(binary(pngBytes))
                )
                .respond(
                        response()
                                .withStatusCode(OK_200.code())
                                .withHeaders(
                                        header(CONTENT_TYPE.toString(), MediaType.PNG.toString()),
                                        header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.png\"; filename=\"test.png\"")
                                )
                                .withBody(binary(pngBytes))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withHeaders(
                                header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.png\"; filename=\"test.png\""),
                                header(CONTENT_TYPE.toString(), MediaType.PNG.toString())
                        )
                        .withBody(binary(pngBytes)),
                makeRequest(
                        request()
                                .withPath(calculatePath("ws/rest/user/1/icon/1.png"))
                                .withBody(binary(pngBytes))
                                .withMethod("POST"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withHeaders(
                                header(CONTENT_DISPOSITION.toString(), "form-data; name=\"test.png\"; filename=\"test.png\""),
                                header(CONTENT_TYPE.toString(), MediaType.PNG.toString())
                        )
                        .withBody(binary(pngBytes)),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("ws/rest/user/1/icon/1.png"))
                                .withBody(binary(pngBytes))
                                .withMethod("POST"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseForExpectationWithDelay() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path1"))
                )
                .respond(
                        response()
                                .withBody("some_body1")
                                .withDelay(new Delay(TimeUnit.MILLISECONDS, 10))
                );
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path2"))
                )
                .respond(
                        response()
                                .withBody("some_body2")
                                .withDelay(new Delay(TimeUnit.MILLISECONDS, 20))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path1")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path1")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethod() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withSecure(true)
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingPathWithNotOperator() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath(not(calculatePath("some_path")))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_other_path")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_other_path")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingMethodWithNotOperator() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod(not("GET"))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("POST"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("POST"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndBody() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody("some_bodyRequest")
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withSecure(true)
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndQueryStringParameters() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withSecure(true)
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndHeaders() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header("requestHeaderNameOne", "requestHeaderValueOne_One", "requestHeaderValueOne_Two"),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header("requestHeaderNameOne", "requestHeaderValueOne_One", "requestHeaderValueOne_Two"),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                )
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withSecure(true)
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header("requestHeaderNameOne", "requestHeaderValueOne_One", "requestHeaderValueOne_Two"),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                )
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndCookies() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withCookies(
                                        cookie("requestCookieNameOne", "requestCookieValueOne"),
                                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                                .withCookies(
                                        cookie("responseCookieNameOne", "responseCookieValueOne"),
                                        cookie("responseCookieNameTwo", "responseCookieValueTwo")
                                )
                );

        // then
        // - in http - cookie objects
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(
                                cookie("responseCookieNameOne", "responseCookieValueOne"),
                                cookie("responseCookieNameTwo", "responseCookieValueTwo")
                        )
                        .withHeaders(
                                header(SET_COOKIE.toString(), "responseCookieNameOne=responseCookieValueOne", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header("headerNameRequest", "headerValueRequest"),
                                        header(CONTENT_TYPE.toString(), TEXT_PLAIN)
                                )
                                .withCookies(
                                        cookie("requestCookieNameOne", "requestCookieValueOne"),
                                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                ),
                        headersToIgnore)
        );
        // - in http - cookie header
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(
                                cookie("responseCookieNameOne", "responseCookieValueOne"),
                                cookie("responseCookieNameTwo", "responseCookieValueTwo")
                        )
                        .withHeaders(
                                header(SET_COOKIE.toString(), "responseCookieNameOne=responseCookieValueOne", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header("headerNameRequest", "headerValueRequest"),
                                        header("Cookie", "requestCookieNameOne=requestCookieValueOne; requestCookieNameTwo=requestCookieValueTwo")
                                ),
                        headersToIgnore)
        );
        // - in https - cookie objects
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(
                                cookie("responseCookieNameOne", "responseCookieValueOne"),
                                cookie("responseCookieNameTwo", "responseCookieValueTwo")
                        )
                        .withHeaders(
                                header(SET_COOKIE.toString(), "responseCookieNameOne=responseCookieValueOne", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withSecure(true)
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header("headerNameRequest", "headerValueRequest")
                                )
                                .withCookies(
                                        cookie("requestCookieNameOne", "requestCookieValueOne"),
                                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                ),
                        headersToIgnore)
        );
        // - in https - cookie header
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(
                                cookie("responseCookieNameOne", "responseCookieValueOne"),
                                cookie("responseCookieNameTwo", "responseCookieValueTwo")
                        )
                        .withHeaders(
                                header(SET_COOKIE.toString(), "responseCookieNameOne=responseCookieValueOne", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withSecure(true)
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header("headerNameRequest", "headerValueRequest"),
                                        header("Cookie", "requestCookieNameOne=requestCookieValueOne; requestCookieNameTwo=requestCookieValueTwo")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndQueryStringParametersAndBodyParameters() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(params(param("bodyParameterName", "bodyParameterValue")))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                );

        // then
        // - in http - url query string
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(params(param("bodyParameterName", "bodyParameterValue"))),
                        headersToIgnore)
        );
        // - in https - query string parameter objects
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(params(param("bodyParameterName", "bodyParameterValue"))),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndQueryStringParametersAndBody() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
                                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // - in http - url query string
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse"),
                                header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in http - query string parameter objects
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse"),
                                header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in https - url string and query string parameter objects
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse"),
                                header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndBodyParameters() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
                                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // - in http - body string
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse"),
                                header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two"))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in http - body parameter objects
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse"),
                                header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in https - url string and query string parameter objects
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse"),
                                header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathAndMethodAndParametersAndHeadersAndCookies() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("PUT")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two"))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest"))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
                                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // - in http - body string
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse"),
                                header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("PUT")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two"))
                                .withHeaders(
                                        header("headerNameRequest", "headerValueRequest"),
                                        header("Cookie", "cookieNameRequest=cookieValueRequest")
                                ),
                        headersToIgnore)
        );
        // - in http - body parameter objects
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse"),
                                header(SET_COOKIE.toString(), "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("PUT")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingBodyParameterWithNotOperatorForNameAndValue() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param(not("bodyParameterOneName"), not("Parameter One Value One"), not("Parameter One Value Two")),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param("OTHERBodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(new StringBody("OTHERBodyParameterOneName=Parameter+One+Value+One" +
                                        "&OTHERBodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two"))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // wrong query string parameter value
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param("bodyParameterOneName", "OTHER Parameter One Value One", "OTHER Parameter One Value Two"),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // wrong query string parameter value
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(new StringBody("bodyParameterOneName=OTHER Parameter+One+Value+One" +
                                        "&bodyParameterOneName=OTHER Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two"))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingBodyParameterWithNotOperatorForName() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param(not("bodyParameterOneName"), string("Parameter One Value One"), string("Parameter One Value Two")),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param("OTHERBodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(new StringBody("OTHERBodyParameterOneName=Parameter+One+Value+One" +
                                        "&OTHERBodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two"))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingBodyParameterWithNotOperatorForValue() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param(string("bodyParameterOneName"), not("Parameter One Value One"), not("Parameter One Value Two")),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string parameter value
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param("bodyParameterOneName", "OTHER Parameter One Value One", "OTHER Parameter One Value Two"),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // wrong query string parameter value
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(new StringBody("bodyParameterOneName=OTHER Parameter+One+Value+One" +
                                        "&bodyParameterOneName=OTHER Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two"))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingQueryStringParameterWithNotOperatorForNameAndValue() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param(not("queryStringParameterOneName"), not("Parameter One Value One"), not("Parameter One Value Two")),
                                        param("queryStringParameterTwoName", "Parameter Two")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("OTHERQueryStringParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("queryStringParameterTwoName", "Parameter Two")
                                ),
                        headersToIgnore)
        );
        // wrong query string parameter value
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "OTHER Parameter One Value One", "OTHER Parameter One Value Two"),
                                        param("queryStringParameterTwoName", "Parameter Two")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingQueryStringParameterWithNotOperatorForName() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param(not("queryStringParameterOneName"), string("Parameter One Value One"), string("Parameter One Value Two")),
                                        param("queryStringParameterTwoName", "Parameter Two")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("OTHERQueryStringParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("queryStringParameterTwoName", "Parameter Two")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingQueryStringParameterWithNotOperatorForValue() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param(string("queryStringParameterOneName"), not("Parameter One Value One"), not("Parameter One Value Two")),
                                        param("queryStringParameterTwoName", "Parameter Two")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string parameter value
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "OTHER Parameter One Value One", "OTHER Parameter One Value Two"),
                                        param("queryStringParameterTwoName", "Parameter Two")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingCookieWithNotOperatorForNameAndValue() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withCookies(
                                        cookie(not("requestCookieNameOne"), not("requestCookieValueOne")),
                                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string cookie name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withCookies(
                                        cookie("OTHERrequestCookieNameOne", "requestCookieValueOne"),
                                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                ),
                        headersToIgnore)
        );
        // wrong query string cookie value
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withCookies(
                                        cookie("requestCookieNameOne", "OTHERrequestCookieValueOne"),
                                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingCookieWithNotOperatorForName() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withCookies(
                                        cookie(not("requestCookieNameOne"), string("requestCookieValueOne")),
                                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string cookie name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withCookies(
                                        cookie("OTHERrequestCookieNameOne", "requestCookieValueOne"),
                                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingCookieWithNotOperatorForValue() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withCookies(
                                        cookie(string("requestCookieNameOne"), not("requestCookieValueOne")),
                                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string cookie value
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withCookies(
                                        cookie("requestCookieNameOne", "OTHERrequestCookieValueOne"),
                                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingHeaderWithNotOperatorForNameAndValue() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header(not("requestHeaderNameOne"), not("requestHeaderValueOne")),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string header name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header("OTHERrequestHeaderNameOne", "requestHeaderValueOne"),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                ),
                        headersToIgnore)
        );
        // wrong query string header value
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header("requestHeaderNameOne", "OTHERrequestHeaderValueOne"),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingHeaderWithNotOperatorForName() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header(not("requestHeaderNameOne"), string("requestHeaderValueOne")),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string header name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header("OTHERrequestHeaderNameOne", "requestHeaderValueOne"),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingHeaderWithNotOperatorForValue() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header(string("requestHeaderNameOne"), not("requestHeaderValueOne")),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string header value
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header("requestHeaderNameOne", "OTHERrequestHeaderValueOne"),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForWhenTimeToLiveExpired() {
        // when
        mockServerClient
                .when(
                        request().withPath(calculatePath("some_path")),
                        exactly(2),
                        TimeToLive.exactly(TimeUnit.SECONDS, 3L)
                )
                .respond(
                        response().withBody("some_body").withDelay(TimeUnit.SECONDS, 3L)
                );

        // then
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );

        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingBody() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue"))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue"))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_other_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_other_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForMatchingBodyWithNotOperator() {
        // when
        mockServerClient
                .when(
                        request()
                                .withBody(Not.not(json("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"))),
                        exactly(2)
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"extra_ignored_field\": \"some value\"," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"extra_ignored_array\": [\"one\", \"two\"]," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingXPathBody() {
        // when
        mockServerClient.when(request().withBody(new XPathBody("/bookstore/book[price>35]/price")), exactly(2)).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody(new StringBody("" +
                                        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + System.getProperty("line.separator") +
                                        "<bookstore>" + System.getProperty("line.separator") +
                                        "  <book category=\"COOKING\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Everyday Italian</title>" + System.getProperty("line.separator") +
                                        "    <author>Giada De Laurentiis</author>" + System.getProperty("line.separator") +
                                        "    <year>2005</year>" + System.getProperty("line.separator") +
                                        "    <price>30.00</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "  <book category=\"CHILDREN\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Harry Potter</title>" + System.getProperty("line.separator") +
                                        "    <author>J K. Rowling</author>" + System.getProperty("line.separator") +
                                        "    <year>2005</year>" + System.getProperty("line.separator") +
                                        "    <price>29.99</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "  <book category=\"WEB\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Learning XML</title>" + System.getProperty("line.separator") +
                                        "    <author>Erik T. Ray</author>" + System.getProperty("line.separator") +
                                        "    <year>2003</year>" + System.getProperty("line.separator") +
                                        "    <price>31.95</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "</bookstore>")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody(new StringBody("" +
                                        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + System.getProperty("line.separator") +
                                        "<bookstore>" + System.getProperty("line.separator") +
                                        "  <book category=\"COOKING\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Everyday Italian</title>" + System.getProperty("line.separator") +
                                        "    <author>Giada De Laurentiis</author>" + System.getProperty("line.separator") +
                                        "    <year>2005</year>" + System.getProperty("line.separator") +
                                        "    <price>30.00</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "  <book category=\"CHILDREN\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Harry Potter</title>" + System.getProperty("line.separator") +
                                        "    <author>J K. Rowling</author>" + System.getProperty("line.separator") +
                                        "    <year>2005</year>" + System.getProperty("line.separator") +
                                        "    <price>29.99</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "  <book category=\"WEB\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Learning XML</title>" + System.getProperty("line.separator") +
                                        "    <author>Erik T. Ray</author>" + System.getProperty("line.separator") +
                                        "    <year>2003</year>" + System.getProperty("line.separator") +
                                        "    <price>31.95</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "</bookstore>")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingXmlBody() {
        // when
        mockServerClient.when(request().withBody(xml("" +
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + System.getProperty("line.separator") +
                "<bookstore>" + System.getProperty("line.separator") +
                "  <book category=\"COOKING\" nationality=\"ITALIAN\">" + System.getProperty("line.separator") +
                "    <title lang=\"en\">Everyday Italian</title>" + System.getProperty("line.separator") +
                "    <author>Giada De Laurentiis</author>" + System.getProperty("line.separator") +
                "    <year>2005</year>" + System.getProperty("line.separator") +
                "    <price>30.00</price>" + System.getProperty("line.separator") +
                "  </book>" + System.getProperty("line.separator") +
                "</bookstore>")), exactly(2)).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody(new StringBody("" +
                                        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + System.getProperty("line.separator") +
                                        "<bookstore>" + System.getProperty("line.separator") +
                                        "  <book category=\"COOKING\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Everyday Italian</title>" + System.getProperty("line.separator") +
                                        "    <author>Giada De Laurentiis</author>" + System.getProperty("line.separator") +
                                        "    <year>2005</year>" + System.getProperty("line.separator") +
                                        "    <price>30.00</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "</bookstore>")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody(new StringBody("" +
                                        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + System.getProperty("line.separator") +
                                        "<bookstore>" + System.getProperty("line.separator") +
                                        "  <book category=\"COOKING\">" + System.getProperty("line.separator") +
                                        "    <title lang=\"en\">Everyday Italian</title>" + System.getProperty("line.separator") +
                                        "    <author>Giada De Laurentiis</author>" + System.getProperty("line.separator") +
                                        "    <year>2005</year>" + System.getProperty("line.separator") +
                                        "    <price>30.00</price>" + System.getProperty("line.separator") +
                                        "  </book>" + System.getProperty("line.separator") +
                                        "</bookstore>")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingJsonBody() {
        // when
        mockServerClient.when(request().withBody(json("{" + System.getProperty("line.separator") +
                "    \"id\": 1," + System.getProperty("line.separator") +
                "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                "    \"price\": 12.50," + System.getProperty("line.separator") +
                "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                "}")), exactly(2)).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"name\": \"---- XXXX WRONG VALUE XXXX ----\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"name\": \"---- XXXX WRONG VALUE XXXX ----\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingJsonBodyWithMatchType() {
        // when
        mockServerClient
                .when(
                        request()
                                .withBody(json("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}", MatchType.STRICT)),
                        exactly(2))
                .respond(
                        response()
                                .withBody("some_body")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"extra field\": \"some value\"," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"extra field\": \"some value\"," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingJsonSchema() {
        // when
        mockServerClient.when(request().withBody(jsonSchema("{" + System.getProperty("line.separator") +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + System.getProperty("line.separator") +
                "    \"title\": \"Product\"," + System.getProperty("line.separator") +
                "    \"description\": \"A product from Acme's catalog\"," + System.getProperty("line.separator") +
                "    \"type\": \"object\"," + System.getProperty("line.separator") +
                "    \"properties\": {" + System.getProperty("line.separator") +
                "        \"id\": {" + System.getProperty("line.separator") +
                "            \"description\": \"The unique identifier for a product\"," + System.getProperty("line.separator") +
                "            \"type\": \"integer\"" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"name\": {" + System.getProperty("line.separator") +
                "            \"description\": \"Name of the product\"," + System.getProperty("line.separator") +
                "            \"type\": \"string\"" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"price\": {" + System.getProperty("line.separator") +
                "            \"type\": \"number\"," + System.getProperty("line.separator") +
                "            \"minimum\": 0," + System.getProperty("line.separator") +
                "            \"exclusiveMinimum\": true" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"tags\": {" + System.getProperty("line.separator") +
                "            \"type\": \"array\"," + System.getProperty("line.separator") +
                "            \"items\": {" + System.getProperty("line.separator") +
                "                \"type\": \"string\"" + System.getProperty("line.separator") +
                "            }," + System.getProperty("line.separator") +
                "            \"minItems\": 1," + System.getProperty("line.separator") +
                "            \"uniqueItems\": true" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"required\": [\"id\", \"name\", \"price\"]" + System.getProperty("line.separator") +
                "}")), exactly(2)).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"wrong field name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST")
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"wrong field name\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingPath() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue"))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue"))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_other_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withSecure(true)
                                .withPath(calculatePath("some_other_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForMatchingPathWithNotOperator() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath(not(calculatePath("some_path")))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForMatchingMethodWithNotOperator() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod(not("GET"))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingBodyParameterName() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
                                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param("OTHERBodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(new StringBody("OTHERBodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two"))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForMatchingBodyParameterWithNotOperator() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param(not("bodyParameterOneName"), not("Parameter One Value One"), not("Parameter One Value Two")),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(new StringBody("bodyParameterOneName=Other Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two"))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingBodyParameterValue() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
                                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // wrong body parameter value
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        param("bodyParameterOneName", "Other Parameter One Value One", "Parameter One Value Two"),
                                        param("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // wrong body parameter value
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(new StringBody("bodyParameterOneName=Other Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two"))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingQueryStringParameterName() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
                                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("OTHERQueryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(
                                        header("headerNameRequest", "headerValueRequest")
                                )
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingQueryStringParameterValue() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
                                .withCookies(cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // wrong query string parameter value
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "OTHERqueryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForMatchingQueryStringParameterWithNotOperator() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param(not("queryStringParameterOneName"), not("Parameter One Value One"), not("Parameter One Value Two")),
                                        param("queryStringParameterTwoName", "Parameter Two")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("queryStringParameterTwoName", "Parameter Two")
                                ),
                        headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("queryStringParameterTwoName", "Parameter Two")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingCookieName() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue"))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue"))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieOtherName", "cookieValue")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieOtherName", "cookieValue")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingCookieValue() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue"))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue"))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieOtherValue")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieOtherValue")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForMatchingCookieWithNotOperator() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withCookies(
                                        cookie(not("requestCookieNameOne"), not("requestCookieValueOne")),
                                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withCookies(
                                        cookie("requestCookieNameOne", "requestCookieValueOne"),
                                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                ),
                        headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withCookies(
                                        cookie("requestCookieNameOne", "requestCookieValueOne"),
                                        cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingHeaderName() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue"))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue"))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerOtherName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerOtherName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForNonMatchingHeaderValue() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue"))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(cookie("cookieName", "cookieValue"))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerOtherValue"))
                                .withCookies(cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withQueryStringParameters(
                                        param("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        param("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerOtherValue"))
                                .withCookies(cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseForMatchingHeaderWithNotOperator() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header(not("requestHeaderNameOne"), not("requestHeaderValueOne")),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                )
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body_response")
                );

        // then
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header("requestHeaderNameOne", "requestHeaderValueOne"),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                ),
                        headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        header("requestHeaderNameOne", "requestHeaderValueOne"),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldVerifyReceivedRequests() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path")), exactly(2)
                )
                .respond(
                        response()
                                .withBody("some_body")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        mockServerClient.verify(request()
                .withPath(calculatePath("some_path")));
        mockServerClient.verify(request()
                .withPath(calculatePath("some_path")), VerificationTimes.exactly(1));

        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        mockServerClient.verify(request().withPath(calculatePath("some_path")), VerificationTimes.atLeast(1));
        mockServerClient.verify(request().withPath(calculatePath("some_path")), VerificationTimes.exactly(2));
    }

    @Test
    public void shouldVerifyReceivedRequestInSsl() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some.*path")), exactly(2)
                )
                .respond(
                        response()
                                .withBody("some_body")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        mockServerClient.verify(request()
                .withPath(calculatePath("some_path"))
                .withSecure(false));
        mockServerClient.verify(request()
                .withPath(calculatePath("some_path"))
                .withSecure(false), VerificationTimes.exactly(1));

        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_secure_path"))
                                .withSecure(true),
                        headersToIgnore)
        );
        mockServerClient.verify(request()
                .withPath(calculatePath("some_secure_path"))
                .withSecure(true));
        mockServerClient.verify(request()
                .withPath(calculatePath("some_secure_path"))
                .withSecure(true), VerificationTimes.exactly(1));
    }

    @Test
    public void shouldVerifyReceivedRequestsWithRegexBody() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_path"))
                                .withBody("{type: 'some_random_type', value: 'some_random_value'}"),
                        exactly(2)
                )
                .respond(
                        response()
                                .withBody("some_response")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_response"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_path"))
                                .withBody("{type: 'some_random_type', value: 'some_random_value'}"),
                        headersToIgnore)
        );
        mockServerClient.verify(
                request()
                        .withBody(regex("\\{type\\: \\'some_random_type\\'\\, value\\: \\'some_random_value\\'\\}"))
        );
        mockServerClient.verify(
                request()
                        .withBody(regex("\\{type\\: \\'some_random_type\\'\\, value\\: \\'some_random_value\\'\\}")),
                VerificationTimes.exactly(1)
        );

        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_response"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withMethod("POST")
                                .withPath(calculatePath("some_path"))
                                .withBody("{type: 'some_random_type', value: 'some_random_value'}"),
                        headersToIgnore)
        );
        mockServerClient.verify(
                request()
                        .withSecure(true)
                        .withBody(regex("\\{type\\: \\'some_random_type\\'\\, value\\: \\'some_random_value\\'\\}"))
        );
        mockServerClient.verify(
                request()
                        .withSecure(true)
                        .withBody(regex("\\{type\\: \\'some_random_type\\'\\, value\\: \\'some_random_value\\'\\}")),
                VerificationTimes.atLeast(1)
        );
    }

    @Test
    public void shouldVerifyReceivedRequestsWithNoBody() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response());

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        mockServerClient.verify(request()
                .withPath(calculatePath("some_path")));
        mockServerClient.verify(request()
                .withPath(calculatePath("some_path")), VerificationTimes.exactly(1));
    }

    @Test
    public void shouldVerifyNotEnoughRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        try {
            mockServerClient.verify(request()
                    .withPath(calculatePath("some_path")), VerificationTimes.atLeast(2));
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found at least 2 times, expected:<{" + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path") + "\"" + System.getProperty("line.separator") +
                    "}> but was:<{" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path") + "\"," + System.getProperty("line.separator")));
        }
    }

    @Test
    public void shouldVerifyTooManyRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        try {
            mockServerClient.verify(request()
                    .withPath(calculatePath("some_path")), VerificationTimes.exactly(0));
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found exactly 0 times, expected:<{" + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path") + "\"" + System.getProperty("line.separator") +
                    "}> but was:<{" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path") + "\"," + System.getProperty("line.separator")));
        }
    }

    @Test
    public void shouldVerifyNoMatchingRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        try {
            mockServerClient.verify(request()
                    .withPath(calculatePath("some_other_path")), VerificationTimes.exactly(2));
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found exactly 2 times, expected:<{" + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_other_path") + "\"" + System.getProperty("line.separator") +
                    "}> but was:<{" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path") + "\"," + System.getProperty("line.separator")));
        }
    }

    @Test
    public void shouldVerifyNoRequestsReceived() {
        // when
        mockServerClient.reset();

        // then
        mockServerClient.verifyZeroInteractions();
    }

    @Test
    public void shouldNotVerifyNoRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        try {
            mockServerClient.verifyZeroInteractions();
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found exactly 0 times, expected:<{ }> but was:<{" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path") + "\"," + System.getProperty("line.separator")));
        }
    }

    @Test
    public void shouldVerifyNoMatchingRequestsReceivedInSsl() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some.*path")), exactly(2)).respond(response().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        try {
            mockServerClient.verify(
                    request()
                            .withPath(calculatePath("some_path"))
                            .withSecure(true),
                    VerificationTimes.atLeast(1)
            );
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found at least once, expected:<{" + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path") + "\"," + System.getProperty("line.separator") +
                    "  \"secure\" : true" + System.getProperty("line.separator") +
                    "}> but was:<{" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path") + "\"," + System.getProperty("line.separator")));
        }

        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_secure_path"))
                                .withSecure(true),
                        headersToIgnore)
        );
        try {
            mockServerClient.verify(
                    request()
                            .withPath(calculatePath("some_secure_path"))
                            .withSecure(false),
                    VerificationTimes.atLeast(1)
            );
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found at least once, expected:<{" + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_secure_path") + "\"," + System.getProperty("line.separator") +
                    "  \"secure\" : false" + System.getProperty("line.separator") +
                    "}> but was:<[ {" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path") + "\"," + System.getProperty("line.separator")));
        }

    }

    @Test
    public void shouldVerifySequenceOfRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(6)).respond(response().withBody("some_body"));

        // then
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withPath(calculatePath("some_path_one")),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withPath(calculatePath("some_path_two")),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withPath(calculatePath("some_path_three")),
                        headersToIgnore)
        );
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_three")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_two")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_two")), request(calculatePath("some_path_three")));
    }

    @Test
    public void shouldVerifySequenceOfRequestsReceivedIncludingThoseNotMatchingAnException() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4)).respond(response().withBody("some_body"));

        // then
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withPath(calculatePath("some_path_one")),
                        headersToIgnore)
        );
        assertEquals(
                notFoundResponse(),
                makeRequest(
                        request().withPath(calculatePath("not_found")),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withPath(calculatePath("some_path_three")),
                        headersToIgnore)
        );
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_three")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("not_found")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("not_found")), request(calculatePath("some_path_three")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("not_found")), request(calculatePath("some_path_three")));
    }

    @Test
    public void shouldRetrieveSequenceOfRequestsReceivedIncludingThoseNotMatchingAnException() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4)).respond(response().withBody("some_body"));
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withPath(calculatePath("some_path_one")),
                        headersToIgnore)
        );
        assertEquals(
                notFoundResponse(),
                makeRequest(
                        request().withPath(calculatePath("not_found")),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withPath(calculatePath("some_path_three")),
                        headersToIgnore)
        );

        // then
        verifyRequestMatches(
                mockServerClient.retrieveRecordedRequests(request().withPath(calculatePath("some_path.*"))),
                request(calculatePath("some_path_one")),
                request(calculatePath("some_path_three"))
        );

        verifyRequestMatches(
                mockServerClient.retrieveRecordedRequests(request()),
                request(calculatePath("some_path_one")),
                request(calculatePath("not_found")),
                request(calculatePath("some_path_three"))
        );

        verifyRequestMatches(
                mockServerClient.retrieveRecordedRequests(null),
                request(calculatePath("some_path_one")),
                request(calculatePath("not_found")),
                request(calculatePath("some_path_three"))
        );
    }

    @Test
    public void shouldRetrieveSequenceOfExpectationsSetup() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(4))
                .respond(response().withBody("some_body"));
        mockServerClient.when(request().withPath(calculatePath("some_path.*")))
                .respond(response().withBody("some_body"));
        mockServerClient.when(request().withPath(calculatePath("some_other_path")))
                .respond(response().withBody("some_other_body"));
        mockServerClient.when(request().withPath(calculatePath("some_forward_path")))
                .forward(forward());

        // then
        assertThat(
                mockServerClient.retrieveExistingExpectations(request().withPath(calculatePath("some_path.*"))),
                arrayContaining(
                        new Expectation(request().withPath(calculatePath("some_path.*")), exactly(4), TimeToLive.unlimited())
                                .thenRespond(response().withBody("some_body")),
                        expectation(request().withPath(calculatePath("some_path.*")))
                                .thenRespond(response().withBody("some_body"))
                )
        );

        assertThat(
                mockServerClient.retrieveExistingExpectations(null),
                arrayContaining(
                        new Expectation(request().withPath(calculatePath("some_path.*")), exactly(4), TimeToLive.unlimited())
                                .thenRespond(response().withBody("some_body")),
                        expectation(request().withPath(calculatePath("some_path.*")))
                                .thenRespond(response().withBody("some_body")),
                        expectation(request().withPath(calculatePath("some_other_path")))
                                .thenRespond(response().withBody("some_other_body")),
                        expectation(request().withPath(calculatePath("some_forward_path")))
                                .thenForward(forward())
                )
        );

        assertThat(
                mockServerClient.retrieveExistingExpectations(request()),
                arrayContaining(
                        new Expectation(request().withPath(calculatePath("some_path.*")), exactly(4), TimeToLive.unlimited())
                                .thenRespond(response().withBody("some_body")),
                        expectation(request().withPath(calculatePath("some_path.*")))
                                .thenRespond(response().withBody("some_body")),
                        expectation(request().withPath(calculatePath("some_other_path")))
                                .thenRespond(response().withBody("some_other_body")),
                        expectation(request().withPath(calculatePath("some_forward_path")))
                                .thenForward(forward())
                )
        );
    }

    @Test
    public void shouldVerifySequenceOfRequestsNotReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(6)).respond(response().withBody("some_body"));

        // then
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withPath(calculatePath("some_path_one")),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withPath(calculatePath("some_path_two")),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withPath(calculatePath("some_path_three")),
                        headersToIgnore)
        );
        try {
            mockServerClient.verify(request(calculatePath("some_path_two")), request(calculatePath("some_path_one")));
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path_two") + "\"" + System.getProperty("line.separator") +
                    "}, {" + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path_one") + "\"" + System.getProperty("line.separator") +
                    "} ]> but was:<[ {" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path_one") + "\"," + System.getProperty("line.separator")));
        }
        try {
            mockServerClient.verify(request(calculatePath("some_path_three")), request(calculatePath("some_path_two")));
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path_three") + "\"" + System.getProperty("line.separator") +
                    "}, {" + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path_two") + "\"" + System.getProperty("line.separator") +
                    "} ]> but was:<[ {" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path_one") + "\"," + System.getProperty("line.separator")));
        }
        try {
            mockServerClient.verify(request(calculatePath("some_path_four")));
            fail();
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request sequence not found, expected:<[ {" + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path_four") + "\"" + System.getProperty("line.separator") +
                    "} ]> but was:<[ {" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"" + calculatePath("some_path_one") + "\"," + System.getProperty("line.separator")));
        }
    }

    @Test
    public void shouldClearExpectationsAndLogs() {
        // given - some expectations
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path1"))
                )
                .respond(
                        response()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path2"))
                )
                .respond(
                        response()
                                .withBody("some_body2")
                );

        // and - some matching requests
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path1")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );

        // when
        mockServerClient
                .clear(
                        request()
                                .withPath(calculatePath("some_path1"))
                );

        // then - expectations cleared
        assertThat(
                mockServerClient.retrieveExistingExpectations(null),
                arrayContaining(
                        expectation(
                                request()
                                        .withPath(calculatePath("some_path2"))
                        )
                                .thenRespond(
                                        response()
                                                .withBody("some_body2")
                                )
                )
        );

        // and then - request log cleared
        verifyRequestMatches(
                mockServerClient.retrieveRecordedRequests(null),
                request(calculatePath("some_path2"))
        );

        // and then - remaining expectations not cleared
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path1")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldClearExpectationsOnly() {
        // given - some expectations
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path1"))
                )
                .respond(
                        response()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path2"))
                )
                .respond(
                        response()
                                .withBody("some_body2")
                );

        // and - some matching requests
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path1")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );

        // when
        mockServerClient
                .clear(
                        request()
                                .withPath(calculatePath("some_path1")),
                        MockServerClient.TYPE.EXPECTATION
                );

        // then - expectations cleared
        assertThat(
                mockServerClient.retrieveExistingExpectations(null),
                arrayContaining(
                        expectation(
                                request()
                                        .withPath(calculatePath("some_path2"))
                        )
                                .thenRespond(
                                        response()
                                                .withBody("some_body2")
                                )
                )
        );

        // and then - request log not cleared
        verifyRequestMatches(
                mockServerClient.retrieveRecordedRequests(null),
                request(calculatePath("some_path1")),
                request(calculatePath("some_path2"))
        );
    }

    @Test
    public void shouldClearLogsOnly() {
        // given - some expectations
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path1"))
                )
                .respond(
                        response()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path2"))
                )
                .respond(
                        response()
                                .withBody("some_body2")
                );

        // and - some matching requests
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path1")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );

        // when
        mockServerClient
                .clear(
                        request()
                                .withPath(calculatePath("some_path1")),
                        MockServerClient.TYPE.LOG
                );

        // then - expectations cleared
        assertThat(
                mockServerClient.retrieveExistingExpectations(null),
                arrayContaining(
                        expectation(
                                request()
                                        .withPath(calculatePath("some_path1"))
                        )
                                .thenRespond(
                                        response()
                                                .withBody("some_body1")
                                ),
                        expectation(
                                request()
                                        .withPath(calculatePath("some_path2"))
                        )
                                .thenRespond(
                                        response()
                                                .withBody("some_body2")
                                )
                )
        );

        // and then - request log cleared
        verifyRequestMatches(
                mockServerClient.retrieveRecordedRequests(null),
                request(calculatePath("some_path2"))
        );
    }

    @Test
    public void shouldClearAllExpectationsWithNull() {
        // given
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path1"))
                )
                .respond(
                        response()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path2"))
                )
                .respond(
                        response()
                                .withBody("some_body2")
                );
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );

        // when
        mockServerClient.clear(null);

        // then
        assertThat(mockServerClient.retrieveExistingExpectations(null), emptyArray());
        assertThat(mockServerClient.retrieveRecordedRequests(null), emptyArray());
    }

    @Test
    public void shouldClearAllExpectationsWithEmptyRequest() {
        // given
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path1"))
                )
                .respond(
                        response()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path2"))
                )
                .respond(
                        response()
                                .withBody("some_body2")
                );
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );

        // when
        mockServerClient.clear(request());

        // then
        assertThat(mockServerClient.retrieveExistingExpectations(null), emptyArray());
        assertThat(mockServerClient.retrieveRecordedRequests(null), emptyArray());
    }

    @Test
    public void shouldClearExpectationsWithXPathBody() {
        // given
        mockServerClient
                .when(
                        request()
                                .withBody(xpath("/bookstore/book[year=2005]/price"))
                )
                .respond(
                        response()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        request()
                                .withBody(xpath("/bookstore/book[year=2006]/price"))
                )
                .respond(
                        response()
                                .withBody("some_body2")
                );

        // and
        StringBody xmlBody = new StringBody("" +
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + System.getProperty("line.separator") +
                "<bookstore>" + System.getProperty("line.separator") +
                "  <book category=\"COOKING\">" + System.getProperty("line.separator") +
                "    <title lang=\"en\">Everyday Italian</title>" + System.getProperty("line.separator") +
                "    <author>Giada De Laurentiis</author>" + System.getProperty("line.separator") +
                "    <year>2005</year>" + System.getProperty("line.separator") +
                "    <price>30.00</price>" + System.getProperty("line.separator") +
                "  </book>" + System.getProperty("line.separator") +
                "  <book category=\"CHILDREN\">" + System.getProperty("line.separator") +
                "    <title lang=\"en\">Harry Potter</title>" + System.getProperty("line.separator") +
                "    <author>J K. Rowling</author>" + System.getProperty("line.separator") +
                "    <year>2006</year>" + System.getProperty("line.separator") +
                "    <price>29.99</price>" + System.getProperty("line.separator") +
                "  </book>" + System.getProperty("line.separator") +
                "  <book category=\"WEB\">" + System.getProperty("line.separator") +
                "    <title lang=\"en\">Learning XML</title>" + System.getProperty("line.separator") +
                "    <author>Erik T. Ray</author>" + System.getProperty("line.separator") +
                "    <year>2003</year>" + System.getProperty("line.separator") +
                "    <price>31.95</price>" + System.getProperty("line.separator") +
                "  </book>" + System.getProperty("line.separator") +
                "</bookstore>");

        // then
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withBody(xmlBody),
                        headersToIgnore)
        );

        // when
        mockServerClient
                .clear(
                        request()
                                .withBody(xpath("/bookstore/book[year=2005]/price"))
                );

        // then
        assertThat(
                mockServerClient.retrieveExistingExpectations(null),
                arrayContaining(
                        expectation(
                                request()
                                        .withBody(xpath("/bookstore/book[year=2006]/price"))
                        )
                                .thenRespond(
                                        response()
                                                .withBody("some_body2")
                                )
                )
        );
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withBody(xmlBody),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withBody(xmlBody),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldClearExpectationsWithJsonSchemaBody() {
        // given
        JsonSchemaBody jsonSchemaBodyOne = jsonSchema("{" + System.getProperty("line.separator") +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + System.getProperty("line.separator") +
                "    \"title\": \"Product\"," + System.getProperty("line.separator") +
                "    \"description\": \"A product from Acme's catalog\"," + System.getProperty("line.separator") +
                "    \"type\": \"object\"," + System.getProperty("line.separator") +
                "    \"properties\": {" + System.getProperty("line.separator") +
                "        \"id\": {" + System.getProperty("line.separator") +
                "            \"description\": \"The unique identifier for a product\"," + System.getProperty("line.separator") +
                "            \"type\": \"integer\"" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"name\": {" + System.getProperty("line.separator") +
                "            \"description\": \"Name of the product\"," + System.getProperty("line.separator") +
                "            \"type\": \"string\"" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"price\": {" + System.getProperty("line.separator") +
                "            \"type\": \"number\"," + System.getProperty("line.separator") +
                "            \"minimum\": 0," + System.getProperty("line.separator") +
                "            \"exclusiveMinimum\": true" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"tags\": {" + System.getProperty("line.separator") +
                "            \"type\": \"array\"," + System.getProperty("line.separator") +
                "            \"items\": {" + System.getProperty("line.separator") +
                "                \"type\": \"string\"" + System.getProperty("line.separator") +
                "            }," + System.getProperty("line.separator") +
                "            \"minItems\": 1," + System.getProperty("line.separator") +
                "            \"uniqueItems\": true" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"required\": [\"id\", \"name\", \"price\"]" + System.getProperty("line.separator") +
                "}");
        JsonSchemaBody jsonSchemaBodyTwo = jsonSchema("{" + System.getProperty("line.separator") +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + System.getProperty("line.separator") +
                "    \"title\": \"Product\"," + System.getProperty("line.separator") +
                "    \"description\": \"A product from Acme's catalog\"," + System.getProperty("line.separator") +
                "    \"type\": \"object\"," + System.getProperty("line.separator") +
                "    \"properties\": {" + System.getProperty("line.separator") +
                "        \"id\": {" + System.getProperty("line.separator") +
                "            \"description\": \"The unique identifier for a product\"," + System.getProperty("line.separator") +
                "            \"type\": \"integer\"" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"name\": {" + System.getProperty("line.separator") +
                "            \"description\": \"Name of the product\"," + System.getProperty("line.separator") +
                "            \"type\": \"string\"" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"price\": {" + System.getProperty("line.separator") +
                "            \"type\": \"number\"," + System.getProperty("line.separator") +
                "            \"minimum\": 10," + System.getProperty("line.separator") +
                "            \"exclusiveMinimum\": true" + System.getProperty("line.separator") +
                "        }," + System.getProperty("line.separator") +
                "        \"tags\": {" + System.getProperty("line.separator") +
                "            \"type\": \"array\"," + System.getProperty("line.separator") +
                "            \"items\": {" + System.getProperty("line.separator") +
                "                \"type\": \"string\"" + System.getProperty("line.separator") +
                "            }," + System.getProperty("line.separator") +
                "            \"minItems\": 1," + System.getProperty("line.separator") +
                "            \"uniqueItems\": true" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"required\": [\"id\", \"name\", \"price\"]" + System.getProperty("line.separator") +
                "}");
        mockServerClient
                .when(
                        request()
                                .withBody(jsonSchemaBodyOne)
                )
                .respond(
                        response()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        request()
                                .withBody(jsonSchemaBodyTwo)
                )
                .respond(
                        response()
                                .withBody("some_body2")
                );

        // then
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );

        // when
        mockServerClient
                .clear(
                        request()
                                .withBody(jsonSchemaBodyOne)
                );

        // then
        assertThat(
                mockServerClient.retrieveExistingExpectations(null),
                arrayContaining(
                        expectation(
                                request()
                                        .withBody(jsonSchemaBodyTwo)
                        )
                                .thenRespond(
                                        response()
                                                .withBody("some_body2")
                                )
                )
        );
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withBody("{" + System.getProperty("line.separator") +
                                        "    \"id\": 1," + System.getProperty("line.separator") +
                                        "    \"name\": \"A green door\"," + System.getProperty("line.separator") +
                                        "    \"price\": 12.50," + System.getProperty("line.separator") +
                                        "    \"tags\": [\"home\", \"green\"]" + System.getProperty("line.separator") +
                                        "}"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldClearExpectationsWithParameterBody() {
        // given
        mockServerClient
                .when(
                        request()
                                .withBody(params(param("bodyParameterNameOne", "bodyParameterValueOne")))
                )
                .respond(
                        response()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        request()
                                .withBody(params(param("bodyParameterNameTwo", "bodyParameterValueTwo")))
                )
                .respond(
                        response()
                                .withBody("some_body2")
                );

        // then
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withBody(params(param("bodyParameterName.*", "bodyParameterValue.*"))),
                        headersToIgnore)
        );

        // when
        mockServerClient
                .clear(
                        request()
                                .withBody(params(param("bodyParameterNameOne", "bodyParameterValueOne")))
                );

        // then
        assertThat(
                mockServerClient.retrieveExistingExpectations(null),
                arrayContaining(
                        expectation(
                                request()
                                        .withBody(params(param("bodyParameterNameTwo", "bodyParameterValueTwo")))
                        )
                                .thenRespond(
                                        response()
                                                .withBody("some_body2")
                                )
                )
        );
        // - in http
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withBody(params(param("bodyParameterName.*", "bodyParameterValue.*"))),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withBody(params(param("bodyParameterName.*", "bodyParameterValue.*"))),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReset() {
        // given
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path1"))
                )
                .respond(
                        response()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path2"))
                )
                .respond(
                        response()
                                .withBody("some_body2")
                );

        // when
        mockServerClient.reset();

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path1")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path1")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldEnsureThatInterruptedRequestsAreVerifiable() throws Exception {
        mockServerClient
                .when(
                        request(calculatePath("delayed"))
                )
                .respond(
                        response("delayed data")
                                .withDelay(new Delay(TimeUnit.SECONDS, 3))
                );

        Future<HttpResponse> delayedFuture = Executors.newSingleThreadExecutor().submit(new Callable<HttpResponse>() {
            @Override
            public HttpResponse call() throws Exception {
                return httpClient.sendRequest(
                        request(addContextToPath(calculatePath("delayed")))
                                .withHeader(HOST.toString(), "localhost:" + getMockServerPort())
                );
            }
        });

        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS); // Let request reach server

        delayedFuture.cancel(true); // Then interrupt requesting thread

        mockServerClient.verify(request(calculatePath("delayed"))); // We should be able to verify request that reached server even though its later interrupted
    }

    @Test
    public void shouldEnsureThatRequestDelaysDoNotAffectOtherRequests() throws Exception {
        mockServerClient
                .when(
                        request("/slow")
                )
                .respond(
                        response("super slow")
                                .withDelay(new Delay(TimeUnit.SECONDS, 5))
                );
        mockServerClient
                .when(
                        request("/fast")
                )
                .respond(
                        response("quite fast")
                );

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        Future<Long> slowFuture = executorService.submit(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                long start = System.currentTimeMillis();
                makeRequest(request("/slow"));
                return System.currentTimeMillis() - start;
            }
        });

        // Let fast request come to the server slightly after slow request
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        Future<Long> fastFuture = executorService.submit(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                long start = System.currentTimeMillis();
                makeRequest(request("/fast"));
                return System.currentTimeMillis() - start;

            }
        });

        Long slowRequestElapsedMillis = slowFuture.get();
        Long fastRequestElapsedMillis = fastFuture.get();

        assertThat("Slow request takes less than expected", slowRequestElapsedMillis, is(greaterThan(5 * 1000L)));
        assertThat("Fast request takes longer than expected", fastRequestElapsedMillis, is(lessThan(3 * 1000L)));
    }

    protected void verifyRequestMatches(HttpRequest[] httpRequests, HttpRequest... httpRequestMatchers) {
        if (httpRequests.length != httpRequestMatchers.length) {
            throw new AssertionError("Number of request matchers does not match number of requests, expected:<" + httpRequestMatchers.length + "> but was:<" + httpRequests.length + ">");
        } else {
            for (int i = 0; i < httpRequestMatchers.length; i++) {
                if (!new HttpRequestMatcher(httpRequestMatchers[i]).matches(httpRequests[i])) {
                    throw new AssertionError("Request does not match request matcher, expected:<" + httpRequestMatchers[i] + "> but was:<" + httpRequests[i] + ">");
                }
            }
        }
    }

    protected HttpResponse makeRequest(HttpRequest httpRequest) {
        return makeRequest(httpRequest, Collections.<String>emptySet());
    }

    protected String calculatePath(String path) {
        return (!path.startsWith("/") ? "/" : "") + path;
    }

    protected String addContextToPath(String path) {
        String cleanedPath = path;
        if (!Strings.isNullOrEmpty(servletContext)) {
            cleanedPath =
                    (!servletContext.startsWith("/") ? "/" : "") +
                            servletContext +
                            (!servletContext.endsWith("/") ? "/" : "") +
                            (cleanedPath.startsWith("/") ? cleanedPath.substring(1) : cleanedPath);
        }
        return (!cleanedPath.startsWith("/") ? "/" : "") + cleanedPath;
    }

    protected HttpResponse makeRequest(HttpRequest httpRequest, Collection<String> headersToIgnore) {
        int attemptsRemaining = 10;
        while (attemptsRemaining > 0) {
            try {
                boolean isSsl = httpRequest.isSecure() != null && httpRequest.isSecure();
                int port = (isSsl ? getMockServerSecurePort() : getMockServerPort());
                httpRequest.withPath(addContextToPath(httpRequest.getPath().getValue()));
                httpRequest.withHeader(HOST.toString(), "localhost:" + port);
                HttpResponse httpResponse = httpClient.sendRequest(httpRequest, new InetSocketAddress("localhost", port));
                List<Header> headers = new ArrayList<Header>();
                for (Header header : httpResponse.getHeaders()) {
                    if (!headersToIgnore.contains(header.getName().getValue().toLowerCase())) {
                        if (header.getName().getValue().equalsIgnoreCase(CONTENT_TYPE.toString())) {
                            // this fixes Tomcat which removes the space between
                            // media type and charset in the Content-Type header
                            for (NottableString value : new ArrayList<NottableString>(header.getValues())) {
                                header.getValues().clear();
                                header.addValues(value.getValue().replace(";charset", "; charset"));
                            }
                            header = header(header.getName().lowercase(), header.getValues());
                        }
                        headers.add(header);
                    }
                }
                httpResponse.withHeaders(headers);
                return httpResponse;
            } catch (SocketConnectionException caught) {
                attemptsRemaining--;
                logger.info("Retrying connection to mock server, attempts remaining: " + attemptsRemaining);
                try {
                    TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }
        throw new RuntimeException("Failed to send request:" + System.getProperty("line.separator") + httpRequest);
    }
}
