package org.mockserver.integration.server;

import org.junit.Test;
import org.mockserver.integration.callback.StaticTestExpectationCallback;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.HttpTemplate;

import javax.script.ScriptEngineManager;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public abstract class SameJVMAbstractClientServerIntegrationTest extends AbstractClientServerIntegrationTest {

    @Test
    public void shouldCallbackToSpecifiedClassWithResponseOnStaticField() {
        // given
        StaticTestExpectationCallback.httpRequests.clear();
        StaticTestExpectationCallback.httpResponse = response()
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
                                .withCallbackClass("org.mockserver.integration.callback.StaticTestExpectationCallback")
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
                                        header("X-Test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_http"),
                        headersToIgnore)
        );
        assertEquals(StaticTestExpectationCallback.httpRequests.get(0).getBody().getValue(), "an_example_body_http");
        assertEquals(StaticTestExpectationCallback.httpRequests.get(0).getPath().getValue(), calculatePath("callback"));

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
                                        header("X-Test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_https"),
                        headersToIgnore
                )
        );
        assertEquals(StaticTestExpectationCallback.httpRequests.get(1).getBody().getValue(), "an_example_body_https");
        assertEquals(StaticTestExpectationCallback.httpRequests.get(1).getPath().getValue(), calculatePath("callback"));
    }

    @Test
    public void shouldReturnResponseFromJavaScriptTemplate() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("some_path"))
                )
                .respond(
                        template(
                                HttpTemplate.TemplateType.JAVASCRIPT,
                                "return {" + NEW_LINE +
                                        "     'statusCode': 200," + NEW_LINE +
                                        "     'cookies': request.cookies," +
                                        "     'body': JSON.stringify(" + NEW_LINE +
                                        "               {" + NEW_LINE +
                                        "                    method: request.method," +
                                        "                    path: request.path," +
                                        "                    body: request.body" +
                                        "               }" + NEW_LINE +
                                        "          )" + NEW_LINE +
                                        "};" + NEW_LINE
                        )
                );

        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {

            // then
            // - in http
            assertEquals(
                    response()
                            .withStatusCode(OK_200.code())
                            .withCookie("name", "value")
                            .withHeader("set-cookie", "name=value")
                            .withBody("{\"method\":\"GET\",\"path\":\"/some_path\",\"body\":\"some_request_body\"}"),
                    makeRequest(
                            request()
                                    .withPath(calculatePath("some_path"))
                                    .withCookie("name", "value")
                                    .withBody("some_request_body"),
                            headersToIgnore)
            );
            // - in https
            assertEquals(
                    response()
                            .withStatusCode(OK_200.code())
                            .withCookie("name", "value")
                            .withHeader("set-cookie", "name=value")
                            .withBody("{\"method\":\"GET\",\"path\":\"/some_path\",\"body\":\"some_request_body\"}"),
                    makeRequest(
                            request()
                                    .withSecure(true)
                                    .withPath(calculatePath("some_path"))
                                    .withCookie("name", "value")
                                    .withBody("some_request_body"),
                            headersToIgnore)
            );

        } else {

            // then
            // - in http
            assertEquals(
                    notFoundResponse(),
                    makeRequest(
                            request()
                                    .withPath(calculatePath("some_path"))
                                    .withCookie("name", "value")
                                    .withBody("some_request_body"),
                            headersToIgnore)
            );
            // - in https
            assertEquals(
                    notFoundResponse(),
                    makeRequest(
                            request()
                                    .withSecure(true)
                                    .withPath(calculatePath("some_path"))
                                    .withCookie("name", "value")
                                    .withBody("some_request_body"),
                            headersToIgnore)
            );

        }
    }
}
