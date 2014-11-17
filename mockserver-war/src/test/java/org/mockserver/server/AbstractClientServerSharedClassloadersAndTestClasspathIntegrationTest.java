package org.mockserver.server;

import org.junit.Test;
import org.mockserver.integration.server.AbstractClientServerSharedClassloadersIntegrationTest;
import org.mockserver.model.HttpStatusCode;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientServerSharedClassloadersAndTestClasspathIntegrationTest extends AbstractClientServerSharedClassloadersIntegrationTest {


    @Test
    public void clientCanCallServerForCallbackInTestClasspathInHTTP() {
        // given
        TestClasspathTestExpectationCallback.httpRequests.clear();
        TestClasspathTestExpectationCallback.httpResponse = response()
                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                .withHeaders(
                        header("x-callback", "test_callback_header")
                )
                .withBody("a_callback_response");

        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("callback"))
                )
                .callback(
                        callback()
                                .withCallbackClass("org.mockserver.server.TestClasspathTestExpectationCallback")
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
                                .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "callback")
                                .withMethod("POST")
                                .withHeaders(
                                        header("X-Test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_http"),
                        headersToIgnore)
        );
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(0).getBody().getValue(), "an_example_body_http");
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(0).getPath(), calculatePath("callback"));

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
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "callback")
                                .withMethod("POST")
                                .withHeaders(
                                        header("X-Test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_https"),
                        headersToIgnore)
        );
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(1).getBody().getValue(), "an_example_body_https");
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(1).getPath(), calculatePath("callback"));
    }
}
