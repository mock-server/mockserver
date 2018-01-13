package org.mockserver.examples.mockserver;

import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import static com.google.common.base.Charsets.UTF_8;
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

    public void classCallback() {
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

    public void objectCallback() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .respond(
                new ExpectationResponseCallback() {
                    @Override
                    public HttpResponse handle(HttpRequest request) {
                        if (request.getMethod().getValue().equals("POST")) {
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
}
