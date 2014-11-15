package org.mockserver.integration.server;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.model.*;
import org.mockserver.verify.VerificationTimes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockserver.configuration.SystemProperties.bufferSize;
import static org.mockserver.configuration.SystemProperties.maxTimeout;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Header.header;
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
                        request()
                                .withURL(baseURL(false) + ""),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withURL(baseURL(true)),
                        headersToIgnore)
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
                                header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_http"),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        header("x-test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_http"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_https"),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        header("x-test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_https"),
                        headersToIgnore)
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
                                header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_http"),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        header("x-test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_http"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_https"),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        header("x-test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_https"),
                        headersToIgnore)
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
                                header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body"),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "echo")
                                .withMethod("POST")
                                .withHeaders(
                                        header("x-test", "test_headers_and_body")
                                )
                                .withBody("an_example_body"),
                        headersToIgnore)
        );
        // - respond
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "test_headers_and_body"),
                        headersToIgnore)
        );
        // - no response or forward
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "test_headers_and_body"),
                        headersToIgnore)
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
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withHeaders(
                                header("x-callback", "test_callback_header")
                        )
                        .withBody("a_callback_response"),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "callback")
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
                                .withURL(baseURL(true) + "callback")
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
                        request()
                                .withURL(baseURL(false) + "some_path")
                                .withMethod("POST"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code()),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "some_path")
                                .withMethod("POST"),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerMatchPath() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath("/some_path1")
                )
                .respond(
                        new HttpResponse()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        request()
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
                        request()
                                .withURL(baseURL(false) + "some_path2")
                                .withPath("/some_path2"),
                        headersToIgnore)
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "some_path1")
                                .withPath("/some_path1"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "some_path2")
                                .withPath("/some_path2"),
                        headersToIgnore)
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "some_path1")
                                .withPath("/some_path1"),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerMatchPathXTimes() {
        // when
        mockServerClient.when(request().withPath("/some_path"), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "some_path")
                                .withPath("/some_path"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "some_path")
                                .withPath("/some_path"),
                        headersToIgnore)
        );
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "some_path")
                                .withPath("/some_path"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "some_path")
                                .withPath("/some_path"),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanVerifyRequestsReceived() {
        // when
        mockServerClient.when(request().withPath("/some_path"), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "some_path")
                                .withPath("/some_path"),
                        headersToIgnore)
        );
        mockServerClient.verify(request()
                .withURL(baseURL(false) + "some_path")
                .withPath("/some_path"));
        mockServerClient.verify(request()
                .withURL(baseURL(false) + "some_path")
                .withPath("/some_path"), VerificationTimes.exactly(1));

        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "some_path")
                                .withPath("/some_path"),
                        headersToIgnore)
        );
        mockServerClient.verify(request()
                .withURL("https{0,1}\\:\\/\\/localhost\\:\\d*\\/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("\\/") ? "\\/" : "") + "some_path")
                .withPath("/some_path"), VerificationTimes.atLeast(1));
        mockServerClient.verify(request()
                .withURL("https{0,1}\\:\\/\\/localhost\\:\\d*\\/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("\\/") ? "\\/" : "") + "some_path")
                .withPath("/some_path"), VerificationTimes.exactly(2));
    }

    @Test
    public void clientCanVerifyRequestsReceivedWithNoBody() {
        // when
        mockServerClient.when(request().withPath("/some_path"), exactly(2)).respond(new HttpResponse());

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code()),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "some_path")
                                .withPath("/some_path"),
                        headersToIgnore)
        );
        mockServerClient.verify(request()
                .withURL(baseURL(false) + "some_path")
                .withPath("/some_path"));
        mockServerClient.verify(request()
                .withURL(baseURL(false) + "some_path")
                .withPath("/some_path"), VerificationTimes.exactly(1));
    }

    @Test
    public void clientCanVerifyNotEnoughRequestsReceived() {
        // when
        mockServerClient.when(request().withPath("/some_path"), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "some_path")
                                .withPath("/some_path"),
                        headersToIgnore)
        );
        try {
            mockServerClient.verify(request()
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
        mockServerClient.when(request().withPath("/some_path"), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "some_path")
                                .withPath("/some_path"),
                        headersToIgnore)
        );
        try {
            mockServerClient.verify(request()
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
        mockServerClient.when(request().withPath("/some_path"), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "some_path")
                                .withPath("/some_path"),
                        headersToIgnore)
        );
        try {
            mockServerClient.verify(request()
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
        mockServerClient.when(request().withPath("/some_path.*"), exactly(6)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_one"),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_two"),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_three"),
                        headersToIgnore)
        );
        mockServerClient.verify(request("/some_path_one"), request("/some_path_three"));
        mockServerClient.verify(request("/some_path_one"), request("/some_path_two"));
        mockServerClient.verify(request("/some_path_one"), request("/some_path_two"), request("/some_path_three"));

        // - in https
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(true) + "some_path_one"),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(true) + "some_path_two"),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(true) + "some_path_three"),
                        headersToIgnore)
        );
        mockServerClient.verify(request("/some_path_one"), request("/some_path_three"));
        mockServerClient.verify(request("/some_path_one"), request("/some_path_two"));
        mockServerClient.verify(request("/some_path_one"), request("/some_path_two"), request("/some_path_three"));
    }


    @Test
    public void clientCanVerifySequenceOfRequestsReceivedEvenThoseNotMatchingAnException() {
        // when
        mockServerClient.when(request().withPath("/some_path.*"), exactly(4)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_one"),
                        headersToIgnore)
        );
        assertEquals(
                notFoundResponse(),
                makeRequest(
                        request().withURL(baseURL(false) + "not_found"),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_three"),
                        headersToIgnore)
        );
        mockServerClient.verify(request("/some_path_one"), request("/some_path_three"));
        mockServerClient.verify(request("/some_path_one"), request("/not_found"));
        mockServerClient.verify(request("/some_path_one"), request("/not_found"), request("/some_path_three"));

        // - in https
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(true) + "some_path_one"),
                        headersToIgnore)
        );
        assertEquals(
                notFoundResponse(),
                makeRequest(
                        request().withURL(baseURL(true) + "not_found"),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(true) + "some_path_three"),
                        headersToIgnore)
        );
        mockServerClient.verify(request("/some_path_one"), request("/some_path_three"));
        mockServerClient.verify(request("/some_path_one"), request("/not_found"));
        mockServerClient.verify(request("/some_path_one"), request("/not_found"), request("/some_path_three"));
    }

    @Test
    public void clientCanVerifySequenceOfRequestsNotReceived() {
        // when
        mockServerClient.when(request().withPath("/some_path.*"), exactly(6)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_one"),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_two"),
                        headersToIgnore)
        );
        assertEquals(
                response("some_body"),
                makeRequest(
                        request().withURL(baseURL(false) + "some_path_three"),
                        headersToIgnore)
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
        mockServerClient.when(request().withBody(xpath("/bookstore/book[price>35]/price")), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
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
                                        "</bookstore>", Body.Type.STRING)),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
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
                                        "</bookstore>", Body.Type.STRING)),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerMatchBodyWithJson() {
        // when
        mockServerClient.when(request().withBody(json("{" + System.getProperty("line.separator") +
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
                        request()
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
                                        "}"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
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
                                        "}"),
                        headersToIgnore)
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
                                        header(HttpHeaders.CONTENT_TYPE, MediaType.PDF.toString()),
                                        header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                        header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                                )
                                .withBody(binary(pdfBytes))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                        )
                        .withBody(binary(pdfBytes)),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "ws/rest/user/1/document/2.pdf")
                                .withMethod("GET"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                        )
                        .withBody(binary(pdfBytes)),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "ws/rest/user/1/document/2.pdf")
                                .withMethod("GET"),
                        headersToIgnore)
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
                                        header(HttpHeaders.CONTENT_TYPE, MediaType.PNG.toString()),
                                        header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                                )
                                .withBody(binary(pngBytes))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                        )
                        .withBody(binary(pngBytes)),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "ws/rest/user/1/icon/1.png")
                                .withMethod("GET"),
                        headersToIgnore)
        );

        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                        )
                        .withBody(binary(pngBytes)),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "ws/rest/user/1/icon/1.png")
                                .withMethod("GET"),
                        headersToIgnore)
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
                                        header(HttpHeaders.CONTENT_TYPE, MediaType.PDF.toString()),
                                        header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                        header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                                )
                                .withBody(binary(pdfBytes))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                        )
                        .withBody(binary(pdfBytes)),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "ws/rest/user/1/document/2.pdf")
                                .withBody(binary(pdfBytes))
                                .withMethod("POST"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"test.pdf\""),
                                header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                        )
                        .withBody(binary(pdfBytes)),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "ws/rest/user/1/document/2.pdf")
                                .withBody(binary(pdfBytes))
                                .withMethod("POST"),
                        headersToIgnore)
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
                                        header(HttpHeaders.CONTENT_TYPE, MediaType.PNG.toString()),
                                        header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                                )
                                .withBody(binary(pngBytes))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                        )
                        .withBody(binary(pngBytes)),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "ws/rest/user/1/icon/1.png")
                                .withBody(binary(pngBytes))
                                .withMethod("POST"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withHeaders(
                                header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"test.png\"; filename=\"test.png\"")
                        )
                        .withBody(binary(pngBytes)),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "ws/rest/user/1/icon/1.png")
                                .withBody(binary(pngBytes))
                                .withMethod("POST"),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerMatchPathWithDelay() {
        // when
        mockServerClient.when(
                request()
                        .withPath("/some_path1")
        ).respond(
                new HttpResponse()
                        .withBody("some_body1")
                        .withDelay(new Delay(TimeUnit.MILLISECONDS, 10))
        );
        mockServerClient.when(
                request()
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
                        request()
                                .withURL(baseURL(false) + "some_path2")
                                .withPath("/some_path2"),
                        headersToIgnore)
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "some_path1")
                                .withPath("/some_path1"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "some_path2")
                                .withPath("/some_path2"),
                        headersToIgnore)
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body1"),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "some_path1")
                                .withPath("/some_path1"),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForGETAndMatchingPath() {
        // when
        mockServerClient
                .when(
                        request()
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
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(false) + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse"),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(true) + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForGETAndMatchingPathAndBody() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/some_pathRequest")
                                .withBody("some_bodyRequest")
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withURL(baseURL(false) + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withURL(baseURL(true) + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForGETAndMatchingPathAndParameters() {
        // when
        mockServerClient
                .when(
                        request()
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
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(true) + "some_pathRequest?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForGETAndMatchingPathAndHeaders() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        header("requestHeaderNameOne", "requestHeaderValueOne_One", "requestHeaderValueOne_Two"),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
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
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        header("requestHeaderNameOne", "requestHeaderValueOne_One", "requestHeaderValueOne_Two"),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                )
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(true) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        header("requestHeaderNameOne", "requestHeaderValueOne_One", "requestHeaderValueOne_Two"),
                                        header("requestHeaderNameTwo", "requestHeaderValueTwo")
                                )
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForGETAndMatchingPathAndCookies() {
        // when
        mockServerClient
                .when(
                        request()
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
        HttpResponse actual = makeRequest(
                request()
                        .withMethod("GET")
                        .withURL(baseURL(false) + "some_pathRequest")
                        .withPath("/some_pathRequest")
                        .withHeaders(
                                header("headerNameRequest", "headerValueRequest")
                        )
                        .withCookies(
                                new Cookie("requestCookieNameOne", "requestCookieValueOne_One", "requestCookieValueOne_Two"),
                                new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                        ),
                headersToIgnore);
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(
                                new Cookie("responseCookieNameOne", "responseCookieValueOne_One", "responseCookieValueOne_Two"),
                                new Cookie("responseCookieNameTwo", "responseCookieValueTwo")
                        )
                        .withHeaders(
                                header("Set-Cookie", "responseCookieNameOne=responseCookieValueOne_One", "responseCookieNameOne=responseCookieValueOne_Two", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                actual
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
                                header("Set-Cookie", "responseCookieNameOne=responseCookieValueOne_One", "responseCookieNameOne=responseCookieValueOne_Two", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        header("headerNameRequest", "headerValueRequest"),
                                        header("Cookie", "requestCookieNameOne=requestCookieValueOne_One; requestCookieNameOne=requestCookieValueOne_Two; requestCookieNameTwo=requestCookieValueTwo")
                                ),
                        headersToIgnore)
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
                                header("Set-Cookie", "responseCookieNameOne=responseCookieValueOne_One", "responseCookieNameOne=responseCookieValueOne_Two", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(true) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        header("headerNameRequest", "headerValueRequest")
                                )
                                .withCookies(
                                        new Cookie("requestCookieNameOne", "requestCookieValueOne_One", "requestCookieValueOne_Two"),
                                        new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                                ),
                        headersToIgnore)
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
                                header("Set-Cookie", "responseCookieNameOne=responseCookieValueOne_One", "responseCookieNameOne=responseCookieValueOne_Two", "responseCookieNameTwo=responseCookieValueTwo")
                        ),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(true) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withHeaders(
                                        header("headerNameRequest", "headerValueRequest"),
                                        header("Cookie", "requestCookieNameOne=requestCookieValueOne_One; requestCookieNameOne=requestCookieValueOne_Two; requestCookieNameTwo=requestCookieValueTwo")
                                ),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForPOSTAndMatchingPathAndParameters() {
        // when
        mockServerClient
                .when(
                        request()
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
                        request()
                                .withMethod("POST")
                                .withURL(baseURL(false) + "some_path" +
                                        "?queryStringParameterOneName=queryStringParameterOneValueOne" +
                                        "&queryStringParameterOneName=queryStringParameterOneValueTwo" +
                                        "&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withBody(params(new Parameter("bodyParameterName", "bodyParameterValue"))),
                        headersToIgnore)
        );
        // - in https - query string parameter objects
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_body"),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withURL(baseURL(true) + "some_path")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(params(new Parameter("bodyParameterName=bodyParameterValue"))),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForPOSTAndMatchingPathBodyAndQueryParameters() {
        // when
        mockServerClient
                .when(
                        request()
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
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
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
                                header("headerNameResponse", "headerValueResponse"),
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withURL(baseURL(false) + "some_pathRequest" +
                                        "?queryStringParameterOneName=queryStringParameterOneValueOne" +
                                        "&queryStringParameterOneName=queryStringParameterOneValueTwo" +
                                        "&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withBody("some_bodyRequest")
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in http - query string parameter objects
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse"),
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in https - url string and query string parameter objects
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse"),
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
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
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForPOSTAndMatchingPathBodyParametersAndQueryParameters() {
        // when
        mockServerClient
                .when(
                        request()
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
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
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
                                header("headerNameResponse", "headerValueResponse"),
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.STRING))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in http - body parameter objects
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse"),
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(params(
                                        new Parameter("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // - in https - url string and query string parameter objects
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse"),
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
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
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerPositiveMatchForPUTAndMatchingPathBodyParametersAndHeadersAndCookies() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("PUT")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.STRING))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_bodyResponse")
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
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
                                header("headerNameResponse", "headerValueResponse"),
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("PUT")
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("bodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.STRING))
                                .withHeaders(
                                        header("headerNameRequest", "headerValueRequest"),
                                        header("Cookie", "cookieNameRequest=cookieValueRequest")
                                ),
                        headersToIgnore)
        );
        // - in http - body parameter objects
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withBody("some_bodyResponse")
                        .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                        .withHeaders(
                                header("headerNameResponse", "headerValueResponse"),
                                header("Set-Cookie", "cookieNameResponse=cookieValueResponse")
                        ),
                makeRequest(
                        request()
                                .withMethod("PUT")
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(params(
                                        new Parameter("bodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchBodyOnly() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(false) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_other_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(true) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_other_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchXPathBodyOnly() {
        // when
        mockServerClient.when(request().withBody(new StringBody("/bookstore/book[price>35]/price", Body.Type.XPATH)), exactly(2)).respond(new HttpResponse().withBody("some_body"));

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
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
                                        "</bookstore>", Body.Type.STRING)),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
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
                                        "</bookstore>", Body.Type.STRING)),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchJsonBodyOnly() {
        // when
        mockServerClient.when(request().withBody(json("{" + System.getProperty("line.separator") +
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
                        request()
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
                                        "}"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
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
                                        "}"),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchPathOnly() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(false) + "some_other_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_other_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(true) + "some_other_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_other_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchQueryStringParameterNameOnly() {
        // when
        mockServerClient
                .when(
                        request()
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
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // wrong query string parameter name
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withURL(baseURL(false) + "some_pathRequest" +
                                        "?OTHERQueryStringParameterOneName=queryStringParameterOneValueOne" +
                                        "&queryStringParameterOneName=queryStringParameterOneValueTwo" +
                                        "&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_pathRequest")
                                .withBody("some_bodyRequest")
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchBodyParameterNameOnly() {
        // when
        mockServerClient
                .when(
                        request()
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
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // wrong query string parameter name
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(params(
                                        new Parameter("OTHERBodyParameterOneName", "Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // wrong query string parameter name
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("OTHERBodyParameterOneName=Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.STRING))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchQueryStringParameterValueOnly() {
        // when
        mockServerClient
                .when(
                        request()
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
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // wrong query string parameter value
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "OTHERqueryStringParameterOneValueOne", "queryStringParameterOneValueTwo"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody("some_bodyRequest")
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchBodyParameterValueOnly() {
        // when
        mockServerClient
                .when(
                        request()
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
                                .withHeaders(header("headerNameResponse", "headerValueResponse"))
                                .withCookies(new Cookie("cookieNameResponse", "cookieValueResponse"))
                );

        // then
        // wrong body parameter value
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(params(
                                        new Parameter("bodyParameterOneName", "Other Parameter One Value One", "Parameter One Value Two"),
                                        new Parameter("bodyParameterTwoName", "Parameter Two")
                                ))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
        // wrong body parameter value
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("POST")
                                .withURL(baseURL(false) + "some_pathRequest")
                                .withPath("/some_pathRequest")
                                .withBody(new StringBody("bodyParameterOneName=Other Parameter+One+Value+One" +
                                        "&bodyParameterOneName=Parameter+One+Value+Two" +
                                        "&bodyParameterTwoName=Parameter+Two", Body.Type.STRING))
                                .withHeaders(header("headerNameRequest", "headerValueRequest"))
                                .withCookies(new Cookie("cookieNameRequest", "cookieValueRequest")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchCookieNameOnly() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(false) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieOtherName", "cookieValue")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(true) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieOtherName", "cookieValue")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchCookieValueOnly() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(false) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieOtherValue")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(true) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieOtherValue")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchHeaderNameOnly() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(false) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerOtherName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(true) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerOtherName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanCallServerNegativeMatchHeaderValueOnly() {
        // when
        mockServerClient
                .when(
                        request()
                                .withMethod("GET")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                )
                .respond(
                        new HttpResponse()
                                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                                .withBody("some_body")
                                .withHeaders(header("headerName", "headerValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue"))
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(false) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerOtherValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withMethod("GET")
                                .withURL(baseURL(true) + "some_path?queryStringParameterOneName=queryStringParameterOneValue&queryStringParameterTwoName=queryStringParameterTwoValue")
                                .withPath("/some_path")
                                .withQueryStringParameters(
                                        new Parameter("queryStringParameterOneName", "queryStringParameterOneValue"),
                                        new Parameter("queryStringParameterTwoName", "queryStringParameterTwoValue")
                                )
                                .withBody(exact("some_body"))
                                .withHeaders(header("headerName", "headerOtherValue"))
                                .withCookies(new Cookie("cookieName", "cookieValue")),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanClearServerExpectations() {
        // given
        mockServerClient
                .when(
                        request()
                                .withPath("/some_path1")
                )
                .respond(
                        new HttpResponse()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        request()
                                .withPath("/some_path2")
                )
                .respond(
                        new HttpResponse()
                                .withBody("some_body2")
                );

        // when
        mockServerClient
                .clear(
                        request()
                                .withPath("/some_path1")
                );

        // then
        // - in http
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "some_path2")
                                .withPath("/some_path2"),
                        headersToIgnore)
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "some_path1")
                                .withPath("/some_path1"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_body2"),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "some_path2")
                                .withPath("/some_path2"),
                        headersToIgnore)
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "some_path1")
                                .withPath("/some_path1"),
                        headersToIgnore)
        );
    }

    @Test
    public void clientCanResetServerExpectations() {
        // given
        mockServerClient
                .when(
                        request()
                                .withPath("/some_path1")
                )
                .respond(
                        new HttpResponse()
                                .withBody("some_body1")
                );
        mockServerClient
                .when(
                        request()
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
                        request()
                                .withURL(baseURL(false) + "some_path1")
                                .withPath("/some_path1"),
                        headersToIgnore)
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withURL(baseURL(false) + "some_path2")
                                .withPath("/some_path2"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "some_path1")
                                .withPath("/some_path1"),
                        headersToIgnore)
        );
        assertEquals(
                new HttpResponse()
                        .withStatusCode(HttpStatusCode.NOT_FOUND_404.code()),
                makeRequest(
                        request()
                                .withURL(baseURL(true) + "some_path2")
                                .withPath("/some_path2"),
                        headersToIgnore)
        );
    }

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

    protected HttpResponse makeRequest(HttpRequest httpRequest, Collection<String> headersToIgnore) {
        HttpResponse httpResponse = httpClient.sendRequest(httpRequest);
        List<Header> headers = new ArrayList<Header>();
        for (Header header : httpResponse.getHeaders()) {
            if (!headersToIgnore.contains(header.getName().toLowerCase())) {
                headers.add(header);
            }
        }
        httpResponse.withHeaders(headers);
        return httpResponse;
    }
}
