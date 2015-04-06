package org.mockserver.integration.server;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.netty.SocketConnectionException;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.matchers.JsonBodyMatchType;
import org.mockserver.model.*;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.*;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpCallback.callback;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.Not.not;
import static org.mockserver.model.OutboundHttpRequest.outboundRequest;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.XPathBody.xpath;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientServerIntegrationTest {

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
            "content-type",
            "content-length",
            "accept-encoding",
            "transfer-encoding"
    );
    // http client
    private NettyHttpClient httpClient = new NettyHttpClient();

    public abstract int getMockServerPort();

    public abstract int getMockServerSecurePort();

    public abstract int getTestServerPort();

    protected String calculatePath(String path) {
        return "/" + path;
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_https"),
                makeRequest(
                        request()
                                .setSecure(true)
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
                                .withScheme(HttpForward.Scheme.HTTPS)
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_https"),
                makeRequest(
                        request()
                                .setSecure(true)
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
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
                                .setSecure(true)
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .setSecure(true)
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
                        .withStatusCode(HttpStatusCode.OK_200.code()),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code()),
                makeRequest(
                        request()
                                .setSecure(true)
                                .withPath(calculatePath("some_path"))
                                .withMethod("POST"),
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .setSecure(true)
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        mockServerClient.verify(request().withPath(calculatePath("some_path")), VerificationTimes.atLeast(1));
        mockServerClient.verify(request().withPath(calculatePath("some_path")), VerificationTimes.exactly(2));
    }

    @Test
    public void shouldVerifyReceivedRequestsWithNoBody() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path")), exactly(2)).respond(response());

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code()),
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
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
    public void shouldVerifySequenceOfRequestsReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(6)).respond(response().withBody("some_body"));

        // then
        // - in http
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

        // - in https
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().setSecure(true)
                                .withPath(calculatePath("some_path_one")),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().setSecure(true)
                                .withPath(calculatePath("some_path_two")),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().setSecure(true)
                                .withPath(calculatePath("some_path_three")),
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
        // - in http
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

        // - in https
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().setSecure(true)
                                .withPath(calculatePath("some_path_one")),
                        headersToIgnore)
        );
        assertEquals(
                notFoundResponse(),
                makeRequest(
                        request().setSecure(true)
                                .withPath(calculatePath("not_found")),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().setSecure(true)
                                .withPath(calculatePath("some_path_three")),
                        headersToIgnore)
        );
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("some_path_three")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("not_found")));
        mockServerClient.verify(request(calculatePath("some_path_one")), request(calculatePath("not_found")), request(calculatePath("some_path_three")));
    }

    @Test
    public void shouldVerifySequenceOfRequestsNotReceived() {
        // when
        mockServerClient.when(request().withPath(calculatePath("some_path.*")), exactly(6)).respond(response().withBody("some_body"));

        // then
        // - in http
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path1")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .setSecure(true)
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .setSecure(true)
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .setSecure(true)
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
                                .setSecure(true)
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .setSecure(true)
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
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .setSecure(true)
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
                                        "}", JsonBodyMatchType.ONLY_MATCHING_FIELDS)),
                        exactly(2)
                )
                .respond(
                        response()
                                .withBody("some_body")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withBody("some_body"),
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
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .setSecure(true)
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .setSecure(true)
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
        byte[] pdfBytes = new byte[1024];
        IOUtils.readFully(getClass().getClassLoader().getResourceAsStream("test.pdf"), pdfBytes);
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("ws/rest/user/[0-9]+/document/[0-9]+\\.pdf"))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.OK_200.code())
                                .withHeaders(
                                        header(HttpHeaders.CONTENT_TYPE, MediaType.PDF.toString()),
                                        header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                        header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                                )
                                .withBody(binary(pdfBytes))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                        )
                        .withBody(binary(pdfBytes)),
                makeRequest(
                        request()
                                .setSecure(true)
                                .withPath(calculatePath("ws/rest/user/1/document/2.pdf"))
                                .withMethod("GET"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnPNGResponseByMatchingPath() throws IOException {
        // when
        byte[] pngBytes = new byte[1024];
        IOUtils.readFully(getClass().getClassLoader().getResourceAsStream("test.png"), pngBytes);
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("ws/rest/user/[0-9]+/icon/[0-9]+\\.png"))
                )
                .respond(
                        response()
                                .withStatusCode(HttpStatusCode.OK_200.code())
                                .withHeaders(
                                        header(HttpHeaders.CONTENT_TYPE, MediaType.PNG.toString()),
                                        header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                                )
                                .withBody(binary(pngBytes))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                        )
                        .withBody(binary(pngBytes)),
                makeRequest(
                        request()
                                .setSecure(true)
                                .withPath(calculatePath("ws/rest/user/1/icon/1.png"))
                                .withMethod("GET"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnPDFResponseByMatchingBinaryPDFBody() throws IOException {
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
                                        header(HttpHeaders.CONTENT_TYPE, MediaType.PDF.toString()),
                                        header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                        header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                                )
                                .withBody(binary(pdfBytes))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                        )
                        .withBody(binary(pdfBytes)),
                makeRequest(
                        request()
                                .setSecure(true)
                                .withPath(calculatePath("ws/rest/user/1/document/2.pdf"))
                                .withBody(binary(pdfBytes))
                                .withMethod("POST"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnPNGResponseByMatchingBinaryPNGBody() throws IOException {
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
                                        header(HttpHeaders.CONTENT_TYPE, MediaType.PNG.toString()),
                                        header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                                )
                                .withBody(binary(pngBytes))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
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
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                        )
                        .withBody(binary(pngBytes)),
                makeRequest(
                        request()
                                .setSecure(true)
                                .withPath(calculatePath("ws/rest/user/1/icon/1.png"))
                                .withBody(binary(pngBytes))
                                .withMethod("POST"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseForExpectationWithDelay() {
        // when
        mockServerClient.when(
                request()
                        .withPath(calculatePath("some_path1"))
        ).respond(
                response()
                        .withBody("some_body1")
                        .withDelay(new Delay(TimeUnit.MILLISECONDS, 10))
        );
        mockServerClient.when(
                request()
                        .withPath(calculatePath("some_path2"))
        ).respond(
                response()
                        .withBody("some_body2")
                        .withDelay(new Delay(TimeUnit.MILLISECONDS, 20))
        );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withPath(calculatePath("some_path1")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .setSecure(true)
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .setSecure(true)
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
                                .setSecure(true)
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
                                .withPath(NottableString.not(calculatePath("some_path")))
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
                                .withMethod(NottableString.not("GET"))
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
                                .setSecure(true)
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
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
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
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .setSecure(true)
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
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
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
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .setSecure(true)
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
        HttpResponse actual = makeRequest(
                request()
                        .withMethod("GET")
                        .withPath(calculatePath("some_pathRequest"))
                        .withHeaders(
                                header("headerNameRequest", "headerValueRequest")
                        )
                        .withCookies(
                                cookie("requestCookieNameOne", "requestCookieValueOne"),
                                cookie("requestCookieNameTwo", "requestCookieValueTwo")
                        ),
                headersToIgnore);
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response")
                        .withCookies(
                                cookie("responseCookieNameOne", "responseCookieValueOne"),
                                cookie("responseCookieNameTwo", "responseCookieValueTwo")
                        )
                        .withHeaders(
                                header("Set-Cookie", "responseCookieNameOne=responseCookieValueOne", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                actual
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
                                header("Set-Cookie", "responseCookieNameOne=responseCookieValueOne", "responseCookieNameTwo=responseCookieValueTwo")
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
                                header("Set-Cookie", "responseCookieNameOne=responseCookieValueOne", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .setSecure(true)
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
                                header("Set-Cookie", "responseCookieNameOne=responseCookieValueOne", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .setSecure(true)
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
                                .setSecure(true)
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
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
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
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
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
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
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
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
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
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
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
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
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
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
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
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
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
    public void shouldReturnResponseByNotMatchingBodyParameterWithNotOperator() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withBody(params(
                                        not(param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two")),
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
                                .withBody(new StringBody("OTHERBodyParameterOneName=Other Parameter+One+Value+One" +
                                        "&OTHERBodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two"))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingQueryStringParameterWithNotOperator() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath(calculatePath("some_pathRequest"))
                                .withQueryStringParameters(
                                        not(param("queryStringParameterOneName", "Parameter One Value One", "Parameter One Value Two")),
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
                                        param("OTHERBodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("queryStringParameterTwoName", "Parameter Two")
                                ),
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
                                .withQueryStringParameters(
                                        param("OTHERBodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        param("queryStringParameterTwoName", "Parameter Two")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByNotMatchingCookieWithNotOperator() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withCookies(
                                        not(cookie("OTHERrequestCookieNameOne", "requestCookieValueOne")),
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
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
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
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
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
    public void shouldReturnResponseByNotMatchingHeaderWithNotOperator() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath(calculatePath("some_pathRequest"))
                                .withHeaders(
                                        not(header("OTHERrequestHeaderNameOne", "requestHeaderValueOne")),
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
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
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
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body_response"),
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
                                .setSecure(true)
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
                                .withBody(not(json("{" + System.getProperty("line.separator") +
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
                                .setSecure(true)
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
                                .setSecure(true)
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
                                .setSecure(true)
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
                                .setSecure(true)
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
                                        "}", JsonBodyMatchType.STRICT)),
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
                                .setSecure(true)
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
                                .setSecure(true)
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
                                .setSecure(true)
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
                                .withPath(NottableString.not(calculatePath("some_path")))
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
                                .withMethod(NottableString.not("GET"))
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
                                        not(param("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two")),
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
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
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
                                        not(param("queryStringParameterOneName", "Parameter One Value One", "Parameter One Value Two")),
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
                                .setSecure(true)
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
                                .setSecure(true)
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
                                        not(cookie("requestCookieNameOne", "requestCookieValueOne")),
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
                                .setSecure(true)
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
                                .setSecure(true)
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
                                        not(header("requestHeaderNameOne", "requestHeaderValueOne")),
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
    public void shouldClearExpectations() {
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
        mockServerClient
                .clear(
                        request()
                                .withPath(calculatePath("some_path1"))
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
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
        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .setSecure(true)
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .setSecure(true)
                                .withPath(calculatePath("some_path1")),
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
                                .setSecure(true)
                                .withPath(calculatePath("some_path1")),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .setSecure(true)
                                .withPath(calculatePath("some_path2")),
                        headersToIgnore)
        );
    }

    protected HttpResponse makeRequest(HttpRequest httpRequest, Collection<String> headersToIgnore) {
        int attemptsRemaining = 10;
        while (attemptsRemaining > 0) {
            try {
                int port = (httpRequest.isSecure() ? getMockServerSecurePort() : getMockServerPort());
                HttpResponse httpResponse = httpClient.sendRequest(outboundRequest("localhost", port, servletContext, httpRequest));
                List<Header> headers = new ArrayList<Header>();
                for (Header header : httpResponse.getHeaders()) {
                    if (!headersToIgnore.contains(header.getName().toLowerCase())) {
                        headers.add(header);
                    }
                }
                httpResponse.withHeaders(headers);
                return httpResponse;
            } catch (SocketConnectionException caught) {
                attemptsRemaining--;
                logger.info("Retrying connection to mock server, attempts remaining: " + attemptsRemaining);
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }
        throw new RuntimeException("Failed to send request:" + System.getProperty("line.separator") + httpRequest);
    }
}
