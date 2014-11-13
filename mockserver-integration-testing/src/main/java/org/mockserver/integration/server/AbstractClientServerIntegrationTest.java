package org.mockserver.integration.server;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.http.NettyHttpClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.callback.PrecannedTestExpectationCallback;
import org.mockserver.model.*;
import org.mockserver.verify.VerificationTimes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockserver.configuration.SystemProperties.bufferSize;
import static org.mockserver.configuration.SystemProperties.maxTimeout;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.HttpCallback.callback;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.StringBody.*;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientServerIntegrationTest {

    protected static MockServerClient mockServerClient;
    protected static String servletContext = "";
    // http client
    private NettyHttpClient httpClient = new NettyHttpClient();

    public AbstractClientServerIntegrationTest() {
        bufferSize(1024);
        maxTimeout(TimeUnit.SECONDS.toMillis(10));
    }

    public abstract int getMockServerPort();

    public abstract int getMockServerSecurePort();

    public abstract int getTestServerPort();

    public abstract int getTestServerSecurePort();

    @Before
    public void resetServer() {
        mockServerClient.reset();
    }

    private String baseURL(boolean secure) {
        String baseUrl;
        if (secure) {
            baseUrl = "https://localhost:" + getMockServerSecurePort();
        } else {
            baseUrl = "http://localhost:" + getMockServerPort();
        }
        return baseUrl + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "");
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
                                .withURL(baseURL(false) + "")
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true))
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
                        .withBody("an_example_body_http"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(false) + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        new Header("X-Test", "test_headers_and_body"),
                                        new Header("Content-Type", "text/plain")
                                )
                                .withBody("an_example_body_http")
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
                        .withBody("an_example_body_https"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        new Header("X-Test", "test_headers_and_body"),
                                        new Header("Host", "127.0.0.1:" + getTestServerPort()),
                                        new Header("Accept-Encoding", "gzip,deflate"),
                                        new Header("Content-Type", "text/plain")
                                )
                                .withBody("an_example_body_https")
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
                        .withBody("an_example_body_http"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(false) + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        new Header("X-Test", "test_headers_and_body"),
                                        new Header("Content-Type", "text/plain")
                                )
                                .withBody("an_example_body_http")
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
                        .withBody("an_example_body_https"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        new Header("X-Test", "test_headers_and_body"),
                                        new Header("Content-Type", "text/plain")
                                )
                                .withBody("an_example_body_https")
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
                                .withURL(baseURL(false) + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        new Header("X-Test", "test_headers_and_body"),
                                        new Header("Content-Type", "text/plain")
                                )
                                .withBody("an_example_body")
                )
        );
        // - respond
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(false) + "test_headers_and_body")
                )
        );
        // - no response or forward
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(false) + "test_headers_and_body")
                )
        );
    }


    @Test
    public void clientCanCallServerForCallbackInHTTP() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath("/callback")
                )
                .callback(
                        callback()
                                .withCallbackClass("org.mockserver.integration.callback.PrecannedTestExpectationCallback")
                );

        // then
        // - in http
        assertEquals(
                PrecannedTestExpectationCallback.httpResponse,
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(false) + "callback")
                                .withMethod("POST")
                                .withHeaders(
                                        new Header("X-Test", "test_headers_and_body"),
                                        new Header("Content-Type", "text/plain")
                                )
                                .withBody("an_example_body_http")
                )
        );

        // - in https
        assertEquals(
                PrecannedTestExpectationCallback.httpResponse,
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "callback")
                                .withMethod("POST")
                                .withHeaders(
                                        new Header("X-Test", "test_headers_and_body"),
                                        new Header("Host", "127.0.0.1:" + getTestServerPort()),
                                        new Header("Accept-Encoding", "gzip,deflate"),
                                        new Header("Content-Type", "text/plain")
                                )
                                .withBody("an_example_body_https")
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
                                .withURL(baseURL(false) + "some_path")
                                .withMethod("POST")
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path")
                                .withMethod("POST")
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
                                .withURL(baseURL(false) + "some_path2")
                                .withPath("/some_path2")
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(false) + "some_path1")
                                .withPath("/some_path1")
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path2")
                                .withPath("/some_path2")
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path1")
                                .withPath("/some_path1")
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
                                .withURL(baseURL(false) + "some_path")
                                .withPath("/some_path")
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path")
                                .withPath("/some_path")
                )
        );
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(false) + "some_path")
                                .withPath("/some_path")
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path")
                                .withPath("/some_path")
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
                                .withURL(baseURL(false) + "some_path")
                                .withPath("/some_path")
                )
        );
        mockServerClient.verify(new HttpRequest()
                .withURL(baseURL(false) + "some_path")
                .withPath("/some_path"));
        mockServerClient.verify(new HttpRequest()
                .withURL(baseURL(false) + "some_path")
                .withPath("/some_path"), VerificationTimes.exactly(1));

        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path")
                                .withPath("/some_path")
                )
        );
        mockServerClient.verify(new HttpRequest()
                .withURL("https{0,1}\\:\\/\\/localhost\\:\\d*\\/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("\\/") ? "\\/" : "") + "some_path")
                .withPath("/some_path"), VerificationTimes.atLeast(1));
        mockServerClient.verify(new HttpRequest()
                .withURL("https{0,1}\\:\\/\\/localhost\\:\\d*\\/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("\\/") ? "\\/" : "") + "some_path")
                .withPath("/some_path"), VerificationTimes.exactly(2));
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
                                .withURL(baseURL(false) + "some_path")
                                .withPath("/some_path")
                )
        );
        mockServerClient.verify(new HttpRequest()
                .withURL(baseURL(false) + "some_path")
                .withPath("/some_path"));
        mockServerClient.verify(new HttpRequest()
                .withURL(baseURL(false) + "some_path")
                .withPath("/some_path"), VerificationTimes.exactly(1));
    }

    @Test
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
                                .withURL(baseURL(false) + "some_path")
                                .withPath("/some_path")
                )
        );
        try {
            mockServerClient.verify(new HttpRequest()
                    .withURL(baseURL(false) + "some_path")
                    .withPath("/some_path"), VerificationTimes.atLeast(2));
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found at least 2 times expected:<{" + System.getProperty("line.separator") +
                    "  \"url\" : \"http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path\"" + System.getProperty("line.separator") +
                    "}> but was:<{" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"url\" : \"http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path\"," + System.getProperty("line.separator")));
        }
    }

    @Test
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
                                .withURL(baseURL(false) + "some_path")
                                .withPath("/some_path")
                )
        );
        try {
            mockServerClient.verify(new HttpRequest()
                    .withURL(baseURL(false) + "some_path")
                    .withPath("/some_path"), VerificationTimes.exactly(0));
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found exactly 0 times expected:<{" + System.getProperty("line.separator") +
                    "  \"url\" : \"http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path\"" + System.getProperty("line.separator") +
                    "}> but was:<{" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"url\" : \"http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path\"," + System.getProperty("line.separator")));
        }
    }

    @Test
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
                                .withURL(baseURL(false) + "some_path")
                                .withPath("/some_path")
                )
        );
        try {
            mockServerClient.verify(new HttpRequest()
                    .withURL(baseURL(false) + "some_other_path")
                    .withPath("/some_other_path"), VerificationTimes.exactly(2));
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found exactly 2 times expected:<{" + System.getProperty("line.separator") +
                    "  \"url\" : \"http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_other_path\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_other_path\"" + System.getProperty("line.separator") +
                    "}> but was:<{" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"url\" : \"http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path\"," + System.getProperty("line.separator")));
        }
    }

    @Test
    public void clientCanVerifySequenceOfRequestsReceived() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path.*"), exactly(6)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_one")
                )
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_two")
                )
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_three")
                )
        );
        mockServerClient.verify(request("/some_path_one"), request("/some_path_three"));
        mockServerClient.verify(request("/some_path_one"), request("/some_path_two"));
        mockServerClient.verify(request("/some_path_one"), request("/some_path_two"), request("/some_path_three"));

        // - in https
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(true) + "some_path_one")
                )
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(true) + "some_path_two")
                )
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(true) + "some_path_three")
                )
        );
        mockServerClient.verify(request("/some_path_one"), request("/some_path_three"));
        mockServerClient.verify(request("/some_path_one"), request("/some_path_two"));
        mockServerClient.verify(request("/some_path_one"), request("/some_path_two"), request("/some_path_three"));
    }


    @Test
    public void clientCanVerifySequenceOfRequestsReceivedEvenThoseNotMatchingAnException() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path.*"), exactly(4)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_one")
                )
        );
        assertEquals(
                notFoundResponse(),
                makeRequest(
                        request().withURL(baseURL(false) + "not_found")
                )
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_three")
                )
        );
        mockServerClient.verify(request("/some_path_one"), request("/some_path_three"));
        mockServerClient.verify(request("/some_path_one"), request("/not_found"));
        mockServerClient.verify(request("/some_path_one"), request("/not_found"), request("/some_path_three"));

        // - in https
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(true) + "some_path_one")
                )
        );
        assertEquals(
                notFoundResponse(),
                makeRequest(
                        request().withURL(baseURL(true) + "not_found")
                )
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(true) + "some_path_three")
                )
        );
        mockServerClient.verify(request("/some_path_one"), request("/some_path_three"));
        mockServerClient.verify(request("/some_path_one"), request("/not_found"));
        mockServerClient.verify(request("/some_path_one"), request("/not_found"), request("/some_path_three"));
    }

    @Test
    public void clientCanVerifySequenceOfRequestsNotReceived() {
        // when
        mockServerClient.when(new HttpRequest().withPath("/some_path.*"), exactly(6)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_one")
                )
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_two")
                )
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_three")
                )
        );
        try {
            mockServerClient.verify(request("/some_path_two"), request("/some_path_one"));
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found {" + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path_one\"" + System.getProperty("line.separator") +
                    "} expected:<[ {" + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path_two\"" + System.getProperty("line.separator") +
                    "}, {" + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path_one\"" + System.getProperty("line.separator") +
                    "} ]> but was:<[ {" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"url\" : \"http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path_one\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path_one\"," + System.getProperty("line.separator")));
        }
        try {
            mockServerClient.verify(request("/some_path_three"), request("/some_path_two"));
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found {" + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path_two\"" + System.getProperty("line.separator") +
                    "} expected:<[ {" + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path_three\"" + System.getProperty("line.separator") +
                    "}, {" + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path_two\"" + System.getProperty("line.separator") +
                    "} ]> but was:<[ {" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"url\" : \"http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path_one\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path_one\"," + System.getProperty("line.separator")));
        }
        try {
            mockServerClient.verify(request("/some_path_four"));
        } catch (AssertionError ae) {
            assertThat(ae.getMessage(), startsWith("Request not found {" + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path_four\"" + System.getProperty("line.separator") +
                    "} expected:<[ {" + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path_four\"" + System.getProperty("line.separator") +
                    "} ]> but was:<[ {" + System.getProperty("line.separator") +
                    "  \"method\" : \"GET\"," + System.getProperty("line.separator") +
                    "  \"url\" : \"http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "some_path_one\"," + System.getProperty("line.separator") +
                    "  \"path\" : \"/some_path_one\"," + System.getProperty("line.separator")));
        }
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
                                .withURL(baseURL(false) + "some_path")
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
                                        "</bookstore>", Body.Type.STRING))
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path")
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
                                        "</bookstore>", Body.Type.STRING))
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
                                .withURL(baseURL(false) + "some_path")
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
                                        "}")
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path")
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
                                        "}")
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
                                .withURL(baseURL(false) + "ws/rest/user/1/document/2.pdf")
                                .withMethod("GET")
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
                                .withURL(baseURL(true) + "ws/rest/user/1/document/2.pdf")
                                .withMethod("GET")
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
                                .withURL(baseURL(false) + "ws/rest/user/1/icon/1.png")
                                .withMethod("GET")
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
                                .withURL(baseURL(true) + "ws/rest/user/1/icon/1.png")
                                .withMethod("GET")
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
                                .withURL(baseURL(false) + "ws/rest/user/1/document/2.pdf")
                                .withBody(binary(pdfBytes))
                                .withMethod("POST")
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
                                .withURL(baseURL(true) + "ws/rest/user/1/document/2.pdf")
                                .withBody(binary(pdfBytes))
                                .withMethod("POST")
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
                                .withURL(baseURL(false) + "ws/rest/user/1/icon/1.png")
                                .withBody(binary(pngBytes))
                                .withMethod("POST")
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
                                .withURL(baseURL(true) + "ws/rest/user/1/icon/1.png")
                                .withBody(binary(pngBytes))
                                .withMethod("POST")
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
                                .withURL(baseURL(false) + "some_path2")
                                .withPath("/some_path2")
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(false) + "some_path1")
                                .withPath("/some_path1")
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path2")
                                .withPath("/some_path2")
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path1")
                                .withPath("/some_path1")
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
                                .withURL(baseURL(false) + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
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
                                .withURL(baseURL(true) + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
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
                                .withURL(baseURL(false) + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
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
                                .withURL(baseURL(true) + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
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
                                .withURL(baseURL(false) + "some_pathRequest")
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
                                .withURL(baseURL(true) + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
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
                                .withURL(baseURL(false) + "some_pathRequest")
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
                                .withURL(baseURL(true) + "some_pathRequest")
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
                                .withURL(baseURL(false) + "some_pathRequest")
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
                                .withURL(baseURL(false) + "some_pathRequest")
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
                                .withURL(baseURL(true) + "some_pathRequest")
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
                                .withURL(baseURL(true) + "some_pathRequest")
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
                                .withURL(baseURL(false) + "some_path" +
                                        "?queryStringParameterOneName=queryStringParameterOneValueOne" +
                                        "&queryStringParameterOneName=queryStringParameterOneValueTwo" +
                                        "&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withBody(params(new Parameter("bodyParameterName", "bodyParameterValue")))
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
                                .withURL(baseURL(true) + "some_path")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(params(new Parameter("bodyParameterName=bodyParameterValue")))
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
                                .withURL(baseURL(false) + "some_pathRequest" +
                                        "?queryStringParameterOneName=queryStringParameterOneValueOne" +
                                        "&queryStringParameterOneName=queryStringParameterOneValueTwo" +
                                        "&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withBody("some_bodyRequest")
                                .withHeaders(new Header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
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
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
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
                                .withURL(baseURL(false) + "some_pathRequest" +
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
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
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
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.STRING))
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
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(params(
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
                                .withURL(baseURL(false) + "some_pathRequest" +
                                        "?bodyParameterOneName=bodyParameterOneValueOne" +
                                        "&bodyParameterOneName=bodyParameterOneValueTwo" +
                                        "&bodyParameterTwoName=bodyParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withBody(params(
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
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.STRING))
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
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(params(
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
                                .withURL(baseURL(false) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_other_body"))
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
                                .withURL(baseURL(true) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_other_body"))
                                .withHeaders(new Header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
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
                                .withURL(baseURL(true) + "some_path")
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
                                        "</bookstore>", Body.Type.STRING))
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path")
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
                                        "</bookstore>", Body.Type.STRING))
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
                                .withURL(baseURL(false) + "some_path")
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
                                        "}")
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path")
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
                                        "}")
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
                                .withURL(baseURL(false) + "some_other_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_other_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
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
                                .withURL(baseURL(true) + "some_other_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_other_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
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
                                .withURL(baseURL(false) + "some_pathRequest" +
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
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(params(
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
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("OTHERBodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.STRING))
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
                                .withURL(baseURL(false) + "some_pathRequest")
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
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(params(
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
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("bodyParameterOneName=Other Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.STRING))
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
                                .withURL(baseURL(false) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
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
                                .withURL(baseURL(true) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
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
                                .withURL(baseURL(false) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
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
                                .withURL(baseURL(true) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
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
                                .withURL(baseURL(false) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
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
                                .withURL(baseURL(true) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
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
                                .withURL(baseURL(false) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
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
                                .withURL(baseURL(true) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
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
                                .withURL(baseURL(false) + "some_path2")
                                .withPath("/some_path2")
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(false) + "some_path1")
                                .withPath("/some_path1")
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path2")
                                .withPath("/some_path2")
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path1")
                                .withPath("/some_path1")
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
                                .withURL(baseURL(false) + "some_path1")
                                .withPath("/some_path1")
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(false) + "some_path2")
                                .withPath("/some_path2")
                )
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path1")
                                .withPath("/some_path1")
                )
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        new HttpRequest()
                                .withURL(baseURL(true) + "some_path2")
                                .withPath("/some_path2")
                )
        );
    }

    protected HttpResponse makeRequest(HttpRequest httpRequest) {
        HttpResponse httpResponse = httpClient.sendRequest(httpRequest);
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
