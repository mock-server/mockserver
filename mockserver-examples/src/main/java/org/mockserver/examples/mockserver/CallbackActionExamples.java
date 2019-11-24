package org.mockserver.examples.mockserver;

import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.ACCEPTED_202;

/**
 * @author jamesdbloom
 */
public class CallbackActionExamples {

    public void responseClassCallback() {
        new ClientAndServer(1080)
            .when(
                request()
                    .withPath("/some.*")
            )
            .respond(
                callback()
                    .withCallbackClass("org.mockserver.examples.mockserver.CallbackActionExamples$TestExpectationResponseCallback")
            );
    }

    public void forwardClassCallback() {
        new ClientAndServer(1080)
            .when(
                request()
                    .withPath("/some.*")
            )
            .forward(
                callback()
                    .withCallbackClass("org.mockserver.examples.mockserver.CallbackActionExamples$TestExpectationForwardCallback")
            );
    }

    public void responseObjectCallback() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .respond(
                httpRequest -> {
                    if (httpRequest.getMethod().getValue().equals("POST")) {
                        return response()
                            .withStatusCode(ACCEPTED_202.code())
                            .withHeaders(
                                header("x-object-callback", "test_object_callback_header")
                            )
                            .withBody("an_object_callback_response");
                    } else {
                        return notFoundResponse();
                    }
                }
            );

    }

    public void createExpectationWithinObjectCallback() {
        MockServerClient mockServerClient = new MockServerClient("localhost", 1080);
        mockServerClient
            .when(
                request()
                    .withPath("/some/path")
            )
            .respond(
                httpRequest -> {
                    if (httpRequest.getMethod().getValue().equals("POST")) {
                        mockServerClient
                            .when(
                                request()
                                    .withPath("/some/otherPath")
                            )
                            .respond(
                                response()
                                    .withBody(httpRequest.getBodyAsString())
                            );
                        return response()
                            .withStatusCode(ACCEPTED_202.code())
                            .withBody("request processed");
                    } else {
                        return notFoundResponse();
                    }
                }
            );
    }

    public void forwardObjectCallback() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                httpRequest -> request()
                    .withPath(httpRequest.getPath())
                    .withMethod("POST")
                    .withHeaders(
                        header("x-callback", "test_callback_header"),
                        header("Content-Length", "a_callback_request".getBytes(UTF_8).length),
                        header("Connection", "keep-alive")
                    )
                    .withBody("a_callback_request")
            );

    }

    public static class TestExpectationResponseCallback implements ExpectationResponseCallback {

        @Override
        public HttpResponse handle(HttpRequest httpRequest) {
            if (httpRequest.getPath().getValue().endsWith("/path")) {
                return response()
                    .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                    .withHeaders(
                        header("x-callback", "test_callback_header"),
                        header("Content-Length", "a_callback_response".getBytes(UTF_8).length),
                        header("Connection", "keep-alive")
                    )
                    .withBody("a_callback_response");
            } else {
                return notFoundResponse();
            }
        }
    }

    public static class TestExpectationForwardCallback implements ExpectationForwardCallback {

        @Override
        public HttpRequest handle(HttpRequest httpRequest) {
            return request()
                .withPath(httpRequest.getPath())
                .withMethod("POST")
                .withHeaders(
                    header("x-callback", "test_callback_header"),
                    header("Content-Length", "a_callback_request".getBytes(UTF_8).length),
                    header("Connection", "keep-alive")
                )
                .withBody("a_callback_request");
        }
    }
}
