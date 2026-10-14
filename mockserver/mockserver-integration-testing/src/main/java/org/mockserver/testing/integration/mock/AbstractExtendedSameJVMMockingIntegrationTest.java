package org.mockserver.testing.integration.mock;

import org.junit.Test;
import org.mockserver.model.CaptureRule;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.HttpTemplate;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;
import org.mockserver.testing.integration.callback.StaticTestExpectationResponseCallback;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.mock.Expectation.when;
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
public abstract class AbstractExtendedSameJVMMockingIntegrationTest extends AbstractExtendedMockingIntegrationTest {

    @Test // same JVM due to dynamic calls to static class
    public void shouldCallbackToSpecifiedClassWithDynamicResponse() {
        // given
        StaticTestExpectationResponseCallback.httpRequests.clear();
        StaticTestExpectationResponseCallback.httpResponse = response()
            .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
            .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
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
            .respond(
                callback()
                    .withCallbackClass(StaticTestExpectationResponseCallback.class)
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
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
                getHeadersToRemove()
            )
        );
        assertEquals(StaticTestExpectationResponseCallback.httpRequests.get(0).getBody().getValue(), "an_example_body_http");
        assertEquals(StaticTestExpectationResponseCallback.httpRequests.get(0).getPath().getValue(), calculatePath("callback"));

        // - in https
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
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
                getHeadersToRemove()
            )
        );
        assertEquals(StaticTestExpectationResponseCallback.httpRequests.get(1).getBody().getValue(), "an_example_body_https");
        assertEquals(StaticTestExpectationResponseCallback.httpRequests.get(1).getPath().getValue(), calculatePath("callback"));
    }

    @Test // same JVM due to issues detecting Nashorn is enabled via Maven plugin
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
                        "     'cookies': [ { name: 'name', value: request.cookies['name'] } ]," +
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

        if (JavaScriptTemplateEngine.isPolyglotAvailable()) {

            // then
            // - in http
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withCookie("name", "value")
                    .withHeader("set-cookie", "name=value")
                    .withBody("{\"method\":\"GET\",\"path\":\"/some_path\",\"body\":\"some_request_body\"}"),
                makeRequest(
                    request()
                        .withPath(calculatePath("some_path"))
                        .withCookie("name", "value")
                        .withBody("some_request_body"),
                    getHeadersToRemove()
                )
            );
            // - in https
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withCookie("name", "value")
                    .withHeader("set-cookie", "name=value")
                    .withBody("{\"method\":\"GET\",\"path\":\"/some_path\",\"body\":\"some_request_body\"}"),
                makeRequest(
                    request()
                        .withSecure(true)
                        .withPath(calculatePath("some_path"))
                        .withCookie("name", "value")
                        .withBody("some_request_body"),
                    getHeadersToRemove()
                )
            );

        } else {

            // then - without the JavaScript engine on the classpath the template fails, which answers 500
            // - in http
            assertJavaScriptTemplateFailure(
                makeRequest(
                    request()
                        .withPath(calculatePath("some_path"))
                        .withCookie("name", "value")
                        .withBody("some_request_body"),
                    getHeadersToRemove()
                )
            );
            // - in https
            assertJavaScriptTemplateFailure(
                makeRequest(
                    request()
                        .withSecure(true)
                        .withPath(calculatePath("some_path"))
                        .withCookie("name", "value")
                        .withBody("some_request_body"),
                    getHeadersToRemove()
                )
            );

        }
    }

    @Test // same JVM due to issues detecting the JavaScript (GraalVM Polyglot) engine via the Maven plugin
    public void shouldCaptureRequestValueAndReuseItInJavaScriptResponseTemplate() {
        // given - a multi-step flow: step 1 captures the order id from the request body into the scenario
        // state key "orderIdJavaScript"; a later request reads it back via scenario.get(...) in a JavaScript
        // response template (the "persist a value across calls" use case)
        mockServerClient.upsert(
            when(request().withMethod("POST").withPath(calculatePath("order/javascript/step1")))
                .withCapture(CaptureRule.capture(CaptureRule.Source.jsonPath, "$.id", "orderIdJavaScript"))
                .thenRespond(response().withStatusCode(OK_200.code()).withBody("{\"step\": 1}"))
        );
        mockServerClient
            .when(request().withMethod("POST").withPath(calculatePath("order/javascript/step3")))
            .respond(
                template(
                    HttpTemplate.TemplateType.JAVASCRIPT,
                    "return { 'statusCode': 200, 'body': 'orderId=' + scenario.get('orderIdJavaScript') };"
                )
            );

        // when - the first request supplies the id (capture fires when this request is served)
        makeRequest(
            request()
                .withMethod("POST")
                .withPath(calculatePath("order/javascript/step1"))
                .withBody("{\"id\": \"ORDER-JAVASCRIPT\"}"),
            getHeadersToRemove()
        );

        if (JavaScriptTemplateEngine.isPolyglotAvailable()) {

            // then - the later request reproduces the captured id in its response body
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withBody("orderId=ORDER-JAVASCRIPT"),
                makeRequest(
                    request()
                        .withMethod("POST")
                        .withPath(calculatePath("order/javascript/step3")),
                    getHeadersToRemove()
                )
            );

        } else {

            // then - without the JavaScript engine on the classpath the template fails, which answers 500
            assertJavaScriptTemplateFailure(
                makeRequest(
                    request()
                        .withMethod("POST")
                        .withPath(calculatePath("order/javascript/step3")),
                    getHeadersToRemove()
                )
            );

        }
    }

    private static void assertJavaScriptTemplateFailure(HttpResponse response) {
        assertEquals(Integer.valueOf(500), response.getStatusCode());
        assertEquals("true", response.getFirstHeader("x-mockserver-template-error"));
        assertTrue(response.getBodyAsString(), response.getBodyAsString().startsWith("JAVASCRIPT response template failed: JavaScript response templates require the GraalJS engine"));
    }

    @Test // same JVM due to issues detecting Nashorn is enabled via Maven plugin
    public void shouldForwardTemplateInJavaScript() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                template(HttpTemplate.TemplateType.JAVASCRIPT,
                    "return {" + NEW_LINE +
                        "    'path' : \"/somePath\"," + NEW_LINE +
                        "    'headers' : [ {" + NEW_LINE +
                        "        'name' : \"Host\"," + NEW_LINE +
                        "        'values' : [ \"127.0.0.1:" + insecureEchoServer.getPort() + "\" ]" + NEW_LINE +
                        "    }, {" + NEW_LINE +
                        "        'name' : \"x-test\"," + NEW_LINE +
                        "        'values' : [ request.headers['x-test'][0] ]" + NEW_LINE +
                        "    } ]," + NEW_LINE +
                        "    'body': \"template_\" + request.body" + NEW_LINE +
                        "};")
                    .withDelay(MILLISECONDS, 10)
            );

        if (JavaScriptTemplateEngine.isPolyglotAvailable()) {

            // then
            // - in http
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("template_an_example_body_http"),
                makeRequest(
                    request()
                        .withPath(calculatePath("echo"))
                        .withMethod("POST")
                        .withHeaders(
                            header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_http"),
                    getHeadersToRemove()
                )
            );
            // - in https
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("template_an_example_body_https"),
                makeRequest(
                    request()
                        .withSecure(true)
                        .withPath(calculatePath("echo"))
                        .withMethod("POST")
                        .withHeaders(
                            header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_https"),
                    getHeadersToRemove()
                )
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
                    getHeadersToRemove()
                )
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
                    getHeadersToRemove()
                )
            );

        }
    }

}
