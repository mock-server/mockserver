package org.mockserver.examples.mockserver;

import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockserver.model.BinaryBody.binary;
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

    public void responseClassCallbackWithClass() {
        new ClientAndServer(1080)
            .when(
                request()
                    .withPath("/some.*")
            )
            .respond(
                callback()
                    .withCallbackClass(CallbackActionExamples.TestExpectationResponseCallback.class)
            );
    }

    public void responseClassCallbackWithString() {
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

    public void forwardClassCallbackWithClass() {
        new ClientAndServer(1080)
            .when(
                request()
                    .withPath("/some.*")
            )
            .forward(
                callback()
                    .withCallbackClass(CallbackActionExamples.TestExpectationForwardCallback.class)
            );
    }

    public void forwardClassCallbackWithString() {
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

    @SuppressWarnings("Convert2Lambda")
    public void responseObjectCallbackJava7() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .respond(
                new ExpectationResponseCallback() {
                    @Override
                    public HttpResponse handle(HttpRequest httpRequest) throws Exception {
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
                }
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

    @SuppressWarnings("Convert2Lambda")
    public void forwardObjectCallbackJava7() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                new ExpectationForwardCallback() {
                    @Override
                    public HttpRequest handle(HttpRequest httpRequest) throws Exception {
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
            );

    }

    public void forwardObjectCallbackFixContentType() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withHeader("Content-Type", "application/encrypted;charset=UTF-8")
            )
            .forward(
                httpRequest ->
                    httpRequest
                        .withBody(binary(httpRequest.getBodyAsRawBytes()))
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

    @SuppressWarnings("Convert2Lambda")
    public void forwardObjectCallbackWithResponseOverrideJava7() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                new ExpectationForwardCallback() {
                    @Override
                    public HttpRequest handle(HttpRequest httpRequest) throws Exception {
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
                },
                new ExpectationForwardAndResponseCallback() {
                    @Override
                    public HttpResponse handle(HttpRequest httpRequest, HttpResponse httpResponse) {
                        return httpResponse
                            .withHeader("x-response-test", "x-response-test")
                            .withBody("some_overridden_response_body");
                    }
                }
            );
    }

    public void forwardObjectCallbackWithResponseOverride() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .forward(
                httpRequest ->
                    request()
                        .withPath(httpRequest.getPath())
                        .withMethod("POST")
                        .withHeaders(
                            header("x-callback", "test_callback_header"),
                            header("Content-Length", "a_callback_request".getBytes(UTF_8).length),
                            header("Connection", "keep-alive")
                        )
                        .withBody("a_callback_request"),
                (httpRequest, httpResponse) ->
                    httpResponse
                        .withHeader("x-response-test", "x-response-test")
                        .withBody("some_overridden_response_body")
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
