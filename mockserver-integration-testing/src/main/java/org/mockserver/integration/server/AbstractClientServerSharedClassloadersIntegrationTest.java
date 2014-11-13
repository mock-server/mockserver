package org.mockserver.integration.server;

import org.junit.Test;
import org.mockserver.integration.callback.StaticTestExpectationCallback;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpCallback.callback;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientServerSharedClassloadersIntegrationTest extends AbstractClientServerIntegrationTest {


    @Test
    public void clientCanCallServerForCallbackInSharedClasspathInHTTP() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath("/callback")
                )
                .callback(
                        callback()
                                .withCallbackClass("org.mockserver.integration.callback.StaticTestExpectationCallback")
                );
        StaticTestExpectationCallback.httpResponse = new HttpResponse()
                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                .withHeaders(
                        new Header("x-callback", "test_callback_header")
                )
                .withBody("a_callback_response");

        // then
        // - in http
        HttpResponse actual = makeRequest(
                new HttpRequest()
                        .withURL("http://localhost:" + getMockServerPort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "callback")
                        .withMethod("POST")
                        .withHeaders(
                                new Header("X-Test", "test_headers_and_body"),
                                new Header("Content-Type", "text/plain")
                        )
                        .withBody("an_example_body_http")
        );
        assertEquals(
                StaticTestExpectationCallback.httpResponse,
                actual
        );
        assertEquals(StaticTestExpectationCallback.httpRequests.get(0).getBody().getValue(), "an_example_body_http");
        assertEquals(StaticTestExpectationCallback.httpRequests.get(0).getPath(), "/callback");

        // - in https
        assertEquals(
                StaticTestExpectationCallback.httpResponse,
                makeRequest(
                        new HttpRequest()
                                .withURL("https://localhost:" + getMockServerSecurePort() + "/" + servletContext + (servletContext.length() > 0 && !servletContext.endsWith("/") ? "/" : "") + "callback")
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
        assertEquals(StaticTestExpectationCallback.httpRequests.get(1).getBody().getValue(), "an_example_body_https");
        assertEquals(StaticTestExpectationCallback.httpRequests.get(1).getPath(), "/callback");
    }
}
