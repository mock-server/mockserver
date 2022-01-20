package org.mockserver.netty.integration.mock;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.model.*;
import org.mockserver.serialization.PortBindingSerializer;
import org.mockserver.socket.PortFactory;
import org.mockserver.streams.IOStreamUtils;
import org.mockserver.testing.integration.mock.AbstractExtendedSameJVMMockingIntegrationTest;
import org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase;
import org.mockserver.verify.VerificationTimes;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.*;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.Parameter.schemaParam;
import static org.mockserver.model.PortBinding.portBinding;
import static org.mockserver.testing.closurecallback.ViaWebSocket.viaWebSocket;
import static org.mockserver.testing.tls.SSLSocketFactory.sslSocketFactory;

/**
 * @author jamesdbloom
 */
public abstract class AbstractExtendedNettyMockingIntegrationTest extends AbstractExtendedSameJVMMockingIntegrationTest {

    @Test
    public void shouldReturnResponseByMatchingUrlEncodedPath() throws UnsupportedEncodingException {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath(URLEncoder.encode("ab@c.de", StandardCharsets.UTF_8.name())))
            )
            .respond(
                response()
                    .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                    .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
                    .withBody("some_body_response")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("ab%40c.de"))
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
                .withReasonPhrase(HttpStatusCode.ACCEPTED_202.reasonPhrase())
                .withBody("some_body_response"),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withSecure(true)
                    .withPath(calculatePath("ab%40c.de"))
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
    public void shouldReturnResponseByMatchingQueryParametersWithPipeDelimitedParameters() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some/path")
                    .withQueryStringParameters(new Parameters(
                        schemaParam("variableO[a-z]{2}", "{" + NEW_LINE +
                            "   \"type\": \"string\"," + NEW_LINE +
                            "   \"pattern\": \"variableOneV[a-z]{4}$\"" + NEW_LINE +
                            "}").withStyle(ParameterStyle.PIPE_DELIMITED),
                        schemaParam("?variableTwo", "{" + NEW_LINE +
                            "   \"type\": \"string\"," + NEW_LINE +
                            "   \"pattern\": \"variableTwoV[a-z]{4}$\"" + NEW_LINE +
                            "}").withStyle(ParameterStyle.PIPE_DELIMITED)
                    ).withKeyMatchStyle(KeyMatchStyle.MATCHING_KEY))
            )
            .respond(
                response()
                    .withStatusCode(200)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path" +
                        "?variableOne=variableOneValaa|variableOneValbb|variableOneValcc" +
                        "&variableTwo=variableTwoValue|variableTwoValue")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path" +
                        "?variableOne=variableOneValab" +
                        "&variableTwo=variableTwoValue")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path" +
                        "?variableOne=variableOneValaa|variableOneValbb|variableOneValcc")),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path" +
                        "?variableOne=variableOneValaax|variableOneValbb|variableOneValcc")),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path" +
                        "?variableOne=variableOneValaa|variableOneValbbx|variableOneValcc")),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path" +
                        "?variableOne=variableOneValaa|variableOneValbb|variableOneValccx")),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path" +
                        "?variableOne=variableOneValaa|variableOneValbb|variableOneValcc" +
                        "&variableTwo=variableTwoOtherValue|variableTwoValue")),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path" +
                        "?variableOne=variableOneValaa|variableOneValbb|variableOneValcc" +
                        "&variableTwo=variableTwoValue|variableTwoOtherValue")),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path" +
                        "?variableOne=variableOneValaax|variableOneValbb|variableOneValcc" +
                        "&variableTwo=variableTwoValue|variableTwoOtherValue")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingPathParametersWithMatrixStyleParameters() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some/path/{variableOne}/{variableTwo}")
                    .withPathParameters(new Parameters(
                        schemaParam("variableO[a-z]{2}", "{" + NEW_LINE +
                            "   \"type\": \"string\"," + NEW_LINE +
                            "   \"pattern\": \"variableOneV[a-z]{4}$\"" + NEW_LINE +
                            "}").withStyle(ParameterStyle.MATRIX_EXPLODED),
                        schemaParam("variableTwo", "{" + NEW_LINE +
                            "   \"type\": \"string\"," + NEW_LINE +
                            "   \"pattern\": \"variableTwoV[a-z]{4}$\"" + NEW_LINE +
                            "}").withStyle(ParameterStyle.MATRIX)
                    ).withKeyMatchStyle(KeyMatchStyle.MATCHING_KEY))
            )
            .respond(
                response()
                    .withStatusCode(200)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path" +
                        "/;variableOne=variableOneValaa;variableOne=variableOneValbb;variableOne=variableOneValcc" +
                        "/;variableTwo=variableTwoValue,variableTwoValue")),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase()),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path" +
                        "/;variableOne=variableOneValab" +
                        "/;variableTwo=variableTwoValue")),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path" +
                        "/;variableOne=variableOneValaa;variableOne=variableOneValbb;variableOne=variableOneValcc" +
                        "/;variableTwo=variableTwoOtherValue,variableTwoValue")),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path" +
                        "/;variableOne=variableOneValaa;variableOne=variableOneValbb;variableOne=variableOneValcc" +
                        "/;variableTwo=variableTwoValue,variableTwoOtherValue")),
                headersToIgnore)
        );
        assertEquals(
            notFoundResponse(),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path" +
                        "/;variableOne=variableOneValaax;variableOne=variableOneValbb;variableOne=variableOneValcc" +
                        "/;variableTwo=variableTwoValue,variableTwoOtherValue")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldRespondByObjectCallbackViaWebSocket() throws Exception {
        viaWebSocket(() -> {
            // when
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("object_callback")),
                    exactly(2)
                )
                .respond(
                    httpRequest -> {
                        HttpRequest expectation = request()
                            .withPath(calculatePath("object_callback"))
                            .withMethod("POST")
                            .withHeaders(
                                header("x-test", "test_headers_and_body")
                            )
                            .withBody("an_example_body_http");
                        if (new MatcherBuilder(mock(MockServerLogger.class)).transformsToMatcher(expectation).matches(null, httpRequest)) {
                            return response()
                                .withStatusCode(ACCEPTED_202.code())
                                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                                .withHeaders(
                                    header("x-object-callback", "test_object_callback_header")
                                )
                                .withBody("an_object_callback_response");
                        } else {
                            return notFoundResponse();
                        }
                    }
                );

            // then
            // - in http
            assertEquals(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withHeaders(
                        header("x-object-callback", "test_object_callback_header")
                    )
                    .withBody("an_object_callback_response"),
                makeRequest(
                    request()
                        .withPath(calculatePath("object_callback"))
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
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withHeaders(
                        header("x-object-callback", "test_object_callback_header")
                    )
                    .withBody("an_object_callback_response"),
                makeRequest(
                    request()
                        .withSecure(true)
                        .withPath(calculatePath("object_callback"))
                        .withMethod("POST")
                        .withHeaders(
                            header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_http"),
                    headersToIgnore
                )
            );
        });
    }

    @Test
    public void shouldRespondByObjectCallbackViaLocalJVM() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("object_callback")),
                exactly(2)
            )
            .respond(
                httpRequest -> {
                    HttpRequest expectation = request()
                        .withPath(calculatePath("object_callback"))
                        .withMethod("POST")
                        .withHeaders(
                            header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_http");
                    if (new MatcherBuilder(mock(MockServerLogger.class)).transformsToMatcher(expectation).matches(null, httpRequest)) {
                        return response()
                            .withStatusCode(ACCEPTED_202.code())
                            .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                            .withHeaders(
                                header("x-object-callback", "test_object_callback_header")
                            )
                            .withBody("an_object_callback_response");
                    } else {
                        return notFoundResponse();
                    }
                }
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withHeaders(
                    header("x-object-callback", "test_object_callback_header")
                )
                .withBody("an_object_callback_response"),
            makeRequest(
                request()
                    .withPath(calculatePath("object_callback"))
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
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withHeaders(
                    header("x-object-callback", "test_object_callback_header")
                )
                .withBody("an_object_callback_response"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("object_callback"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore
            )
        );
    }

    private int objectCallbackCounter = 0;

    @Test
    public void shouldRespondByMultipleParallelObjectCallbacksViaWebSocket() throws Exception {
        viaWebSocket(() -> {
            // when
            objectCallbackCounter = 0;
            for (int i = 0; i < 25; i++) {
                mockServerClient
                    .when(
                        request()
                            .withPath(calculatePath("object_callback_" + objectCallbackCounter)),
                        once()
                    )
                    .respond(httpRequest -> {
                        MILLISECONDS.sleep(10);
                        return response()
                            .withStatusCode(ACCEPTED_202.code())
                            .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                            .withHeaders(
                                header("x-object-callback", "test_object_callback_header_" + objectCallbackCounter)
                            )
                            .withBody("an_object_callback_response_" + objectCallbackCounter);
                    });
                objectCallbackCounter++;
            }

            objectCallbackCounter = 0;

            // then
            for (int i = 0; i < 25; i++) {
                assertEquals(
                    response()
                        .withStatusCode(ACCEPTED_202.code())
                        .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                        .withHeaders(
                            header("x-object-callback", "test_object_callback_header_" + objectCallbackCounter)
                        )
                        .withBody("an_object_callback_response_" + objectCallbackCounter),
                    makeRequest(
                        request()
                            .withPath(calculatePath("object_callback_" + objectCallbackCounter))
                            .withMethod("POST")
                            .withHeaders(
                                header("x-test", "test_headers_and_body")
                            )
                            .withBody("an_example_body_http"),
                        headersToIgnore
                    )
                );
                objectCallbackCounter++;
            }
        });
    }

    @Test
    public void shouldRespondByMultipleParallelObjectCallbacksViaLocalJVM() {
        // when
        objectCallbackCounter = 0;
        for (int i = 0; i < 25; i++) {
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("object_callback_" + objectCallbackCounter)),
                    once()
                )
                .respond(httpRequest -> {
                    MILLISECONDS.sleep(10);
                    return response()
                        .withStatusCode(ACCEPTED_202.code())
                        .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                        .withHeaders(
                            header("x-object-callback", "test_object_callback_header_" + objectCallbackCounter)
                        )
                        .withBody("an_object_callback_response_" + objectCallbackCounter);
                });
            objectCallbackCounter++;
        }

        objectCallbackCounter = 0;

        // then
        for (int i = 0; i < 25; i++) {
            assertEquals(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withHeaders(
                        header("x-object-callback", "test_object_callback_header_" + objectCallbackCounter)
                    )
                    .withBody("an_object_callback_response_" + objectCallbackCounter),
                makeRequest(
                    request()
                        .withPath(calculatePath("object_callback_" + objectCallbackCounter))
                        .withMethod("POST")
                        .withHeaders(
                            header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_http"),
                    headersToIgnore
                )
            );
            objectCallbackCounter++;
        }
    }

    @Test
    public void shouldRespondByObjectCallbackAndVerifyRequestsViaWebSocket() throws Exception {
        viaWebSocket(() -> {
            // when
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("object_callback")),
                    once()
                )
                .respond(
                    httpRequest -> response()
                        .withStatusCode(ACCEPTED_202.code())
                        .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                        .withBody("an_object_callback_response")
                );

            // then - return response
            assertEquals(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("an_object_callback_response"),
                makeRequest(
                    request()
                        .withPath(calculatePath("object_callback")),
                    headersToIgnore
                )
            );

            // then - verify request
            mockServerClient
                .verify(
                    request()
                        .withPath(calculatePath("object_callback")),
                    VerificationTimes.once()
                );
            // then - verify no request
            mockServerClient
                .verify(
                    request()
                        .withPath(calculatePath("some_other_path")),
                    VerificationTimes.exactly(0)
                );
        });
    }

    @Test
    public void shouldRespondByObjectCallbackAndVerifyRequestsViaLocalJVM() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("object_callback")),
                once()
            )
            .respond(
                httpRequest -> response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withBody("an_object_callback_response")
            );

        // then - return response
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withBody("an_object_callback_response"),
            makeRequest(
                request()
                    .withPath(calculatePath("object_callback")),
                headersToIgnore
            )
        );

        // then - verify request
        mockServerClient
            .verify(
                request()
                    .withPath(calculatePath("object_callback")),
                VerificationTimes.once()
            );
        // then - verify no request
        mockServerClient
            .verify(
                request()
                    .withPath(calculatePath("some_other_path")),
                VerificationTimes.exactly(0)
            );
    }

    @Test
    public void shouldRespondByObjectCallbackForVeryLargeRequestAndResponsesViaWebSocket() throws Exception {
        viaWebSocket(() -> {
            int bytes = 65536 * 10;
            char[] chars = new char[bytes];
            Arrays.fill(chars, 'a');
            final String veryLargeString = new String(chars);

            // when
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("object_callback")),
                    once()
                )
                .respond(
                    httpRequest -> response()
                        .withBody(veryLargeString)
                );

            // then
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withBody(veryLargeString),
                makeRequest(
                    request()
                        .withPath(calculatePath("object_callback"))
                        .withMethod("POST")
                        .withBody(veryLargeString),
                    headersToIgnore
                )
            );
        });
    }

    @Test
    public void shouldRespondByObjectCallbackForVeryLargeRequestAndResponsesViaLocalJVM() {
        int bytes = 65536 * 10;
        char[] chars = new char[bytes];
        Arrays.fill(chars, 'a');
        final String veryLargeString = new String(chars);

        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("object_callback")),
                once()
            )
            .respond(
                httpRequest -> response()
                    .withBody(veryLargeString)
            );

        // then
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody(veryLargeString),
            makeRequest(
                request()
                    .withPath(calculatePath("object_callback"))
                    .withMethod("POST")
                    .withBody(veryLargeString),
                headersToIgnore
            )
        );
    }

    @Test
    public void shouldForwardByObjectCallbackViaWebSocket() throws Exception {
        viaWebSocket(() -> {
            // when
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("echo"))
                )
                .forward(
                    httpRequest -> request()
                        .withHeader("Host", "localhost:" + (httpRequest.isSecure() ? secureEchoServer.getPort() : insecureEchoServer.getPort()))
                        .withHeader("x-test", httpRequest.getFirstHeader("x-test"))
                        .withBody("some_overridden_body")
                        .withSecure(httpRequest.isSecure())
                );

            // then
            // - in http
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("some_overridden_body"),
                makeRequest(
                    request()
                        .withPath(calculatePath("echo"))
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
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header("x-test", "test_headers_and_body_https")
                    )
                    .withBody("some_overridden_body"),
                makeRequest(
                    request()
                        .withSecure(true)
                        .withPath(calculatePath("echo"))
                        .withMethod("POST")
                        .withHeaders(
                            header("x-test", "test_headers_and_body_https")
                        )
                        .withBody("an_example_body_https"),
                    headersToIgnore)
            );
        });
    }

    @Test
    public void shouldForwardByObjectCallbackViaLocalJVM() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                httpRequest -> request()
                    .withHeader("Host", "localhost:" + (httpRequest.isSecure() ? secureEchoServer.getPort() : insecureEchoServer.getPort()))
                    .withHeader("x-test", httpRequest.getFirstHeader("x-test"))
                    .withBody("some_overridden_body")
                    .withSecure(httpRequest.isSecure())
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
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
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body_https")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body_https")
                    )
                    .withBody("an_example_body_https"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldForwardByObjectCallbackViaLocalJVMWithPathVariables() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath("/some/path/{variableOne}/{variableTwo}")
                    .withPathParameters(
                        schemaParam("variableO[a-z]{2}", "{" + NEW_LINE +
                            "   \"type\": \"string\"," + NEW_LINE +
                            "   \"pattern\": \"variableOneV[a-z]{4}$\"" + NEW_LINE +
                            "}"),
                        schemaParam("variableTwo", "{" + NEW_LINE +
                            "   \"type\": \"string\"," + NEW_LINE +
                            "   \"pattern\": \"variableTwoV[a-z]{4}$\"" + NEW_LINE +
                            "}")
                    )
            )
            .forward(
                httpRequest -> request()
                    .withHeader("Host", "localhost:" + (httpRequest.isSecure() ? secureEchoServer.getPort() : insecureEchoServer.getPort()))
                    .withHeader("x-test", httpRequest.getFirstHeader("x-test"))
                    .withBody("some_overridden_body " + httpRequest.getPathParameters())
                    .withSecure(httpRequest.isSecure())
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body {" + NEW_LINE +
                    "  \"variableTwo\" : [ \"variableTwoValue\" ]," + NEW_LINE +
                    "  \"variableOne\" : [ \"variableOneValue\" ]" + NEW_LINE +
                    "}"),
            makeRequest(
                request()
                    .withPath(calculatePath("some/path/variableOneValue/variableTwoValue"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore
            )
        );
    }

    @Test
    public void shouldForwardRequestAndResponseByObjectCallbackOverrideViaWebSocket() throws Exception {
        viaWebSocket(() -> {
            // when
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("echo"))
                )
                .forward(
                    httpRequest ->
                        request()
                            .withHeader("Host", "localhost:" + (httpRequest.isSecure() ? secureEchoServer.getPort() : insecureEchoServer.getPort()))
                            .withHeader("x-test", httpRequest.getFirstHeader("x-test"))
                            .withBody("some_overridden_body")
                            .withSecure(httpRequest.isSecure()),
                    (httpRequest, httpResponse) ->
                        httpResponse
                            .withHeader("x-response-test", "x-response-test")
                            .removeHeader(CONTENT_LENGTH.toString())
                            .withBody("some_overidden_response_body")
                );

            // then
            // - in http
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header("x-response-test", "x-response-test"),
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("some_overidden_response_body"),
                makeRequest(
                    request()
                        .withPath(calculatePath("echo"))
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
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header("x-response-test", "x-response-test"),
                        header("x-test", "test_headers_and_body_https")
                    )
                    .withBody("some_overidden_response_body"),
                makeRequest(
                    request()
                        .withSecure(true)
                        .withPath(calculatePath("echo"))
                        .withMethod("POST")
                        .withHeaders(
                            header("x-test", "test_headers_and_body_https")
                        )
                        .withBody("an_example_body_https"),
                    headersToIgnore)
            );
        });
    }

    @Test
    public void shouldForwardRequestAndResponseByObjectCallbackOverrideViaLocalJVM() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                httpRequest ->
                    request()
                        .withHeader("Host", "localhost:" + (httpRequest.isSecure() ? secureEchoServer.getPort() : insecureEchoServer.getPort()))
                        .withHeader("x-test", httpRequest.getFirstHeader("x-test"))
                        .withBody("some_overridden_body")
                        .withSecure(httpRequest.isSecure()),
                (httpRequest, httpResponse) ->
                    httpResponse
                        .withHeader("x-response-test", "x-response-test")
                        .removeHeader(CONTENT_LENGTH.toString())
                        .withBody("some_overidden_response_body")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-response-test", "x-response-test"),
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overidden_response_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
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
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-response-test", "x-response-test"),
                    header("x-test", "test_headers_and_body_https")
                )
                .withBody("some_overidden_response_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body_https")
                    )
                    .withBody("an_example_body_https"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldForwardByObjectCallbackWithSocketAddressViaWebSocket() throws Exception {
        viaWebSocket(() -> {
            // when
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("echo"))
                )
                .forward(
                    httpRequest -> request()
                        .withHeader("Host", "incorrect_host:1234")
                        .withHeader("x-test", httpRequest.getFirstHeader("x-test"))
                        .withBody("some_overridden_body")
                        .withSecure(httpRequest.isSecure())
                        .withSocketAddress(
                            "localhost",
                            httpRequest.isSecure() ? secureEchoServer.getPort() : insecureEchoServer.getPort(),
                            httpRequest.isSecure() ? SocketAddress.Scheme.HTTPS : SocketAddress.Scheme.HTTP
                        )
                );

            // then
            // - in http
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("some_overridden_body"),
                makeRequest(
                    request()
                        .withPath(calculatePath("echo"))
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
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header("x-test", "test_headers_and_body_https")
                    )
                    .withBody("some_overridden_body"),
                makeRequest(
                    request()
                        .withSecure(true)
                        .withPath(calculatePath("echo"))
                        .withMethod("POST")
                        .withHeaders(
                            header("x-test", "test_headers_and_body_https")
                        )
                        .withBody("an_example_body_https"),
                    headersToIgnore)
            );
        });
    }

    @Test
    public void shouldForwardByObjectCallbackWithSocketAddressViaLocalJVM() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                httpRequest -> request()
                    .withHeader("Host", "incorrect_host:1234")
                    .withHeader("x-test", httpRequest.getFirstHeader("x-test"))
                    .withBody("some_overridden_body")
                    .withSecure(httpRequest.isSecure())
                    .withSocketAddress(
                        "localhost",
                        httpRequest.isSecure() ? secureEchoServer.getPort() : insecureEchoServer.getPort(),
                        httpRequest.isSecure() ? SocketAddress.Scheme.HTTPS : SocketAddress.Scheme.HTTP
                    )
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("echo"))
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
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body_https")
                )
                .withBody("some_overridden_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body_https")
                    )
                    .withBody("an_example_body_https"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldBindToNewSocketAndReturnStatus() throws InterruptedException {
        // given
        int firstNewPort = PortFactory.findFreePort();
        int secondNewPort = PortFactory.findFreePort();
        PortBindingSerializer portBindingSerializer = new PortBindingSerializer(new MockServerLogger());
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody(json(portBindingSerializer.serialize(
                    portBinding(getServerPort())
                ), MediaType.JSON_UTF_8)),
            makeRequest(
                request()
                    .withPath(calculatePath("mockserver/status"))
                    .withMethod("PUT"),
                headersToIgnore)
        );
        MILLISECONDS.sleep(100);

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody(json(portBindingSerializer.serialize(
                    portBinding(firstNewPort)
                ), MediaType.JSON_UTF_8)),
            makeRequest(
                request()
                    .withPath(calculatePath("mockserver/bind"))
                    .withMethod("PUT")
                    .withBody("{" + NEW_LINE +
                        "  \"ports\" : [ " + firstNewPort + " ]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody(json(portBindingSerializer.serialize(
                    portBinding(this.getServerPort(), firstNewPort)
                ), MediaType.JSON_UTF_8)),
            makeRequest(
                request()
                    .withPath(calculatePath("mockserver/status"))
                    .withMethod("PUT"),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody(json(portBindingSerializer.serialize(
                    portBinding(secondNewPort)
                ), MediaType.JSON_UTF_8)),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("mockserver/bind"))
                    .withMethod("PUT")
                    .withBody("{" + NEW_LINE +
                        "  \"ports\" : [ " + secondNewPort + " ]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody(json(portBindingSerializer.serialize(
                    portBinding(getServerSecurePort(), firstNewPort, secondNewPort)
                ), MediaType.JSON_UTF_8)),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("mockserver/status"))
                    .withMethod("PUT")
                    .withBody("{" + NEW_LINE +
                        "  \"ports\" : [ " + firstNewPort + " ]" + NEW_LINE +
                        "}"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldErrorWhenBindingToUnavailableSocket() throws InterruptedException, IOException {
        ServerSocket server = null;
        try {
            // given
            server = new ServerSocket(0);
            int newPort = server.getLocalPort();

            // when
            HttpResponse response = makeRequest(
                request()
                    .withPath(calculatePath("mockserver/bind"))
                    .withMethod("PUT")
                    .withBody("{" + NEW_LINE +
                        "  \"ports\" : [ " + newPort + " ]" + NEW_LINE +
                        "}"),
                headersToIgnore);

            // then
            assertThat(response.getStatusCode(), is(400));
            assertThat(response.getBodyAsString(), containsString("Exception while binding MockServer to port " + newPort));

        } finally {
            if (server != null) {
                server.close();
                // allow time for the socket to be released
                TimeUnit.MILLISECONDS.sleep(50);
            }
        }
    }

    @Test
    public void shouldReturnResponseWithConnectionOptionsAndKeepAliveFalseAndContentLengthOverride() {
        // given
        List<String> headersToIgnore = new ArrayList<>(AbstractMockingIntegrationTestBase.headersToIgnore);
        headersToIgnore.remove("connection");
        headersToIgnore.remove("content-length");

        // when
        mockServerClient
            .when(
                request()
            )
            .respond(
                response()
                    .withBody("some_long_body")
                    .withConnectionOptions(
                        connectionOptions()
                            .withKeepAliveOverride(false)
                            .withContentLengthHeaderOverride("some_long_body".length() / 2)
                    )
            );

        // then
        // - in http
        assertEquals(
            response()
                .withHeader(CONNECTION.toString(), "close")
                .withHeader(header(CONTENT_LENGTH.toString(), "some_long_body".length() / 2))
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_lo"),
            makeRequest(
                request()
                    .withPath(calculatePath("")),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withHeader(CONNECTION.toString(), "close")
                .withHeader(header(CONTENT_LENGTH.toString(), "some_long_body".length() / 2))
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_lo"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseWithCustomReasonPhrase() {
        // when
        mockServerClient
            .when(
                request()
            )
            .respond(
                response()
                    .withBody("some_body")
                    .withReasonPhrase("someReasonPhrase")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withReasonPhrase("someReasonPhrase")
                .withBody("some_body"),
            makeRequest(
                request()
                    .withPath(calculatePath("")),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withReasonPhrase("someReasonPhrase")
                .withBody("some_body"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldNotReturnResponseByMatchingPathInReverse() {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("/api/0/applications/43d05a04-eb1d-462e-933e-3b3b4592e1c8/experiments"))
                    .withHeader("Content-Type", "application/json"),
                exactly(2),
                TimeToLive.unlimited()
            )
            .respond(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header("Content-Type", "application/json; charset=utf-8"),
                        header("Cache-Control", "no-cache, no-store")
                    )
                    .withBody("[{\"_id\":\"f26b3bfe-a6c2-4aa4-8376-bbba44b75ae6\",\"_applicationId\":\"43d05a04-eb1d-462e-933e-3b3b4592e1c8\",\"name\":\"You can't connect the pixel without programming the redundant RAM system!\",\"url\":\"https://jeremie.info\"}]")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(NOT_FOUND_404.code())
                .withReasonPhrase(NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withPath(calculatePath("/api/0/applications/([0-9a-zA-Z-]+)/experiments"))
                    .withHeader("Content-Type", "application/json"),
                headersToIgnore
            )
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(NOT_FOUND_404.code())
                .withReasonPhrase(NOT_FOUND_404.reasonPhrase()),
            makeRequest(
                request()
                    .withMethod("GET")
                    .withSecure(true)
                    .withPath(calculatePath("/api/0/applications/([0-9a-zA-Z-]+)/experiments"))
                    .withHeader("Content-Type", "application/json"),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseByMatchingVeryLargeHeader() {
        // when
        String largeHeaderValue = RandomStringUtils.randomAlphanumeric(1024 * 2 * 2 * 2 * 2);
        mockServerClient
            .when(
                request()
                    .withHeader("largeHeader", largeHeaderValue)
            )
            .respond(
                response()
                    .withBody("some_string_body_response")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_string_body_response"),
            makeRequest(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
                    .withHeader("largeHeader", largeHeaderValue),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody("some_string_body_response"),
            makeRequest(
                request()
                    .withSecure(true)
                    .withMethod("POST")
                    .withPath(calculatePath("some_path"))
                    .withHeader("largeHeader", largeHeaderValue),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseWithConnectionOptionsAndKeepAliveTrueAndContentLengthOverride() {
        // given
        List<String> headersToIgnore = new ArrayList<>(AbstractMockingIntegrationTestBase.headersToIgnore);
        headersToIgnore.remove("connection");
        headersToIgnore.remove("content-length");

        // when
        mockServerClient
            .when(
                request()
            )
            .respond(
                response()
                    .withBody(binary("some_long_body".getBytes(UTF_8)))
                    .withHeader(CONTENT_TYPE.toString(), MediaType.ANY_AUDIO_TYPE.toString())
                    .withConnectionOptions(
                        connectionOptions()
                            .withKeepAliveOverride(true)
                            .withContentLengthHeaderOverride("some_long_body".length() / 2)
                    )
            );

        // then
        // - in http
        assertEquals(
            response()
                .withHeader(CONNECTION.toString(), "keep-alive")
                .withHeader(header(CONTENT_LENGTH.toString(), "some_long_body".length() / 2))
                .withHeader(CONTENT_TYPE.toString(), MediaType.ANY_AUDIO_TYPE.toString())
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody(binary("some_lo".getBytes(UTF_8), MediaType.ANY_AUDIO_TYPE)),
            makeRequest(
                request()
                    .withPath(calculatePath("")),
                headersToIgnore)
        );
        // - in https
        assertEquals(
            response()
                .withHeader(CONNECTION.toString(), "keep-alive")
                .withHeader(header(CONTENT_LENGTH.toString(), "some_long_body".length() / 2))
                .withHeader(CONTENT_TYPE.toString(), MediaType.ANY_AUDIO_TYPE.toString())
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody(binary("some_lo".getBytes(UTF_8), MediaType.ANY_AUDIO_TYPE)),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("")),
                headersToIgnore)
        );
    }

    @Test
    public void shouldReturnResponseWithConnectionOptionsAndCloseSocketAndSuppressContentLength() throws Exception {
        // when
        mockServerClient
            .when(
                request()
            )
            .respond(
                response()
                    .withBody(binary("some_long_body".getBytes(UTF_8)))
                    .withHeader(CONTENT_TYPE.toString(), MediaType.ANY_AUDIO_TYPE.toString())
                    .withConnectionOptions(
                        connectionOptions()
                            .withCloseSocket(true)
                            .withSuppressContentLengthHeader(true)
                    )
            );

        // then
        // - in http
        try (Socket socket = new Socket("localhost", this.getServerPort())) {
            // given
            OutputStream output = socket.getOutputStream();

            // when
            output.write(("" +
                "GET " + calculatePath("") + " HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertThat(IOStreamUtils.readInputStreamToString(socket), is("" +
                "HTTP/1.1 200 OK" + NEW_LINE +
                "content-type: audio/*" + NEW_LINE +
                "connection: keep-alive" + NEW_LINE
            ));

            TimeUnit.SECONDS.sleep(3);

            // and - socket is closed
            try {
                // flush data to increase chance that Java / OS notice socket has been closed
                output.write("some_random_bytes".getBytes(StandardCharsets.UTF_8));
                output.flush();
                output.write("some_random_bytes".getBytes(StandardCharsets.UTF_8));
                output.flush();

                TimeUnit.SECONDS.sleep(2);

                IOStreamUtils.readInputStreamToString(socket);
                fail("Expected socket read to fail because the socket was closed / reset");
            } catch (SocketException se) {
                assertThat(se.getMessage(), anyOf(
                    containsString("Broken pipe"),
                    containsString("(broken pipe)"),
                    containsString("Connection reset"),
                    containsString("Protocol wrong type"),
                    containsString("Software caused connection abort")
                ));
            }
        }

        // and
        // - in https
        try (SSLSocket sslSocket = sslSocketFactory().wrapSocket(new Socket("localhost", this.getServerPort()))) {
            OutputStream output = sslSocket.getOutputStream();

            // when
            output.write(("" +
                "GET " + calculatePath("") + " HTTP/1.1" + NEW_LINE +
                "Content-Length: 0" + NEW_LINE +
                NEW_LINE
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertThat(IOStreamUtils.readInputStreamToString(sslSocket), is("" +
                "HTTP/1.1 200 OK" + NEW_LINE +
                "content-type: audio/*" + NEW_LINE +
                "connection: keep-alive" + NEW_LINE
            ));
        }
    }

    @Test
    public void shouldReturnChunkedResponseWithConnectionOptions() throws Exception {
        // when
        mockServerClient
            .when(
                request()
            )
            .respond(
                response()
                    .withBody("some_long_body")
                    .withConnectionOptions(
                        connectionOptions()
                            .withCloseSocket(true)
                            .withChunkSize(10)
                    )
            );

        // then
        // - in http
        try (Socket socket = new Socket("localhost", this.getServerPort())) {
            // given
            OutputStream output = socket.getOutputStream();

            // when
            output.write(("" +
                "GET " + calculatePath("") + " HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            String actual = IOUtils.toString(socket.getInputStream(), StandardCharsets.UTF_8.name());
            assertThat(actual, is("HTTP/1.1 200 OK\r\n" +
                "connection: keep-alive\r\n" +
                "transfer-encoding: chunked\r\n" +
                "\r\n" +
                "a\r\n" +
                "some_long_\r\n" +
                "4\r\n" +
                "body\r\n" +
                "0\r\n" +
                "\r\n"
            ));

        }

        // and
        // - in https
        try (SSLSocket sslSocket = sslSocketFactory().wrapSocket(new Socket("localhost", this.getServerPort()))) {
            OutputStream output = sslSocket.getOutputStream();

            // when
            output.write(("" +
                "GET " + calculatePath("") + " HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            String actual = IOUtils.toString(sslSocket.getInputStream(), StandardCharsets.UTF_8.name());
            assertThat(actual, is("HTTP/1.1 200 OK\r\n" +
                "connection: keep-alive\r\n" +
                "transfer-encoding: chunked\r\n" +
                "\r\n" +
                "a\r\n" +
                "some_long_\r\n" +
                "4\r\n" +
                "body\r\n" +
                "0\r\n" +
                "\r\n"
            ));
        }
    }

    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void shouldReturnErrorResponseForExpectationWithHttpError() throws Exception {
        // when
        mockServerClient
            .when(
                request()
                    .withMethod("POST")
                    .withPath(calculatePath("/some_error"))
            )
            .error(
                error()
                    .withResponseBytes("b00m".getBytes(StandardCharsets.UTF_8))
                    .withDropConnection(false)
            );

        // then
        try (Socket socket = new Socket("localhost", this.getServerPort())) {
            // given
            OutputStream output = socket.getOutputStream();

            // when
            output.write(("" +
                "POST " + calculatePath("/some_error") + " HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            byte[] bytes = new byte[4];
            socket.getInputStream().read(bytes);
            assertThat(new String(bytes, StandardCharsets.UTF_8), is("b00m"));
        }

        // and
        // - in https
        try (SSLSocket sslSocket = sslSocketFactory().wrapSocket(new Socket("localhost", this.getServerPort()))) {
            OutputStream output = sslSocket.getOutputStream();

            // when
            output.write(("" +
                "POST " + calculatePath("/some_error") + " HTTP/1.1" + NEW_LINE +
                "Content-Length: 0" + NEW_LINE +
                NEW_LINE
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            byte[] bytes = new byte[4];
            sslSocket.getInputStream().read(bytes);
            assertThat(new String(bytes, StandardCharsets.UTF_8), is("b00m"));
        }
    }

    @Test
    public void shouldReturnErrorResponseForExpectationWithHttpErrorWithConnectionClosed() throws Exception {
        // when
        mockServerClient
            .when(
                request()
            )
            .error(
                error()
                    .withDropConnection(true)
                    .withResponseBytes("some_random_bytes".getBytes(UTF_8))
            );

        // then
        // - in http
        try (Socket socket = new Socket("localhost", this.getServerPort())) {
            // given
            OutputStream output = socket.getOutputStream();

            // when
            output.write(("" +
                "GET " + calculatePath("") + " HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertThat(IOUtils.toString(socket.getInputStream(), StandardCharsets.UTF_8.name()), is("some_random_bytes"));
        }

        // and
        // - in https
        try (SSLSocket sslSocket = sslSocketFactory().wrapSocket(new Socket("localhost", this.getServerPort()))) {
            OutputStream output = sslSocket.getOutputStream();

            // when
            output.write(("" +
                "GET " + calculatePath("") + " HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertThat(IOUtils.toString(sslSocket.getInputStream(), StandardCharsets.UTF_8.name()), is("some_random_bytes"));
        }
    }

    @Test
    public void shouldReturnErrorResponseForExpectationWithHttpErrorAndVerifyRequests() throws Exception {
        // when
        mockServerClient
            .when(
                request(calculatePath("http_error"))
            )
            .error(
                error()
                    .withDropConnection(true)
                    .withResponseBytes("some_random_bytes".getBytes(UTF_8))
            );

        // then
        try (Socket socket = new Socket("localhost", this.getServerPort())) {
            // given
            OutputStream output = socket.getOutputStream();

            // when
            output.write(("" +
                "GET " + calculatePath("http_error") + " HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertThat(IOUtils.toString(socket.getInputStream(), StandardCharsets.UTF_8.name()), is("some_random_bytes"));
        }

        // then - verify request
        mockServerClient
            .verify(
                request()
                    .withPath(calculatePath("http_error")),
                VerificationTimes.once()
            );
        // then - verify no request
        mockServerClient
            .verify(
                request()
                    .withPath(calculatePath("some_other_path")),
                VerificationTimes.exactly(0)
            );
    }

    @Test
    public void shouldCallbackToSpecifiedClassInTestClasspathAsString() {
        // given
        TestClasspathTestExpectationResponseCallback.httpRequests.clear();
        TestClasspathTestExpectationResponseCallback.httpResponse = response()
            .withStatusCode(ACCEPTED_202.code())
            .withReasonPhrase(ACCEPTED_202.reasonPhrase())
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
                    .withCallbackClass("org.mockserver.netty.integration.mock.TestClasspathTestExpectationResponseCallback")
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
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
        assertEquals(TestClasspathTestExpectationResponseCallback.httpRequests.get(0).getBody().getValue(), "an_example_body_http");
        assertEquals(TestClasspathTestExpectationResponseCallback.httpRequests.get(0).getPath().getValue(), calculatePath("callback"));

        // - in https
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
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
                headersToIgnore)
        );
        assertEquals(TestClasspathTestExpectationResponseCallback.httpRequests.get(1).getBody().getValue(), "an_example_body_https");
        assertEquals(TestClasspathTestExpectationResponseCallback.httpRequests.get(1).getPath().getValue(), calculatePath("callback"));
    }

    @Test
    public void shouldCallbackToSpecifiedClassInTestClasspathAsClass() {
        // given
        TestClasspathTestExpectationResponseCallback.httpRequests.clear();
        TestClasspathTestExpectationResponseCallback.httpResponse = response()
            .withStatusCode(ACCEPTED_202.code())
            .withReasonPhrase(ACCEPTED_202.reasonPhrase())
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
                    .withCallbackClass(TestClasspathTestExpectationResponseCallback.class)
            );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
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
        assertEquals(TestClasspathTestExpectationResponseCallback.httpRequests.get(0).getBody().getValue(), "an_example_body_http");
        assertEquals(TestClasspathTestExpectationResponseCallback.httpRequests.get(0).getPath().getValue(), calculatePath("callback"));

        // - in https
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
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
                headersToIgnore)
        );
        assertEquals(TestClasspathTestExpectationResponseCallback.httpRequests.get(1).getBody().getValue(), "an_example_body_https");
        assertEquals(TestClasspathTestExpectationResponseCallback.httpRequests.get(1).getPath().getValue(), calculatePath("callback"));
    }

    @Test
    public void shouldRespondByObjectCallbackWithDelayViaWebSocket() throws Exception {
        viaWebSocket(() -> {
            // when
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("object_callback"))
                )
                .respond(
                    httpRequest -> {
                        HttpRequest expectation = request()
                            .withPath(calculatePath("object_callback"))
                            .withMethod("POST")
                            .withHeaders(
                                header("x-test", "test_headers_and_body")
                            )
                            .withBody("an_example_body_http");
                        if (new MatcherBuilder(mock(MockServerLogger.class)).transformsToMatcher(expectation).matches(null, httpRequest)) {
                            return response()
                                .withStatusCode(ACCEPTED_202.code())
                                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                                .withHeaders(
                                    header("x-object-callback", "test_object_callback_header")
                                )
                                .withBody("an_object_callback_response");
                        } else {
                            return notFoundResponse();
                        }
                    },
                    new Delay(SECONDS, 2)
                );

            // then
            long timeBeforeRequest = System.currentTimeMillis();
            HttpResponse httpResponse = makeRequest(
                request()
                    .withPath(calculatePath("object_callback"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore
            );
            long timeAfterRequest = System.currentTimeMillis();

            // and
            assertEquals(
                response()
                    .withStatusCode(ACCEPTED_202.code())
                    .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                    .withHeaders(
                        header("x-object-callback", "test_object_callback_header")
                    )
                    .withBody("an_object_callback_response"),
                httpResponse
            );
            assertThat(timeAfterRequest - timeBeforeRequest, greaterThanOrEqualTo(MILLISECONDS.toMillis(1900)));
            assertThat(timeAfterRequest - timeBeforeRequest, lessThanOrEqualTo(SECONDS.toMillis(4)));
        });
    }

    @Test
    public void shouldRespondByObjectCallbackWithDelayViaLocalJVM() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("object_callback"))
            )
            .respond(
                httpRequest -> {
                    HttpRequest expectation = request()
                        .withPath(calculatePath("object_callback"))
                        .withMethod("POST")
                        .withHeaders(
                            header("x-test", "test_headers_and_body")
                        )
                        .withBody("an_example_body_http");
                    if (new MatcherBuilder(mock(MockServerLogger.class)).transformsToMatcher(expectation).matches(null, httpRequest)) {
                        return response()
                            .withStatusCode(ACCEPTED_202.code())
                            .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                            .withHeaders(
                                header("x-object-callback", "test_object_callback_header")
                            )
                            .withBody("an_object_callback_response");
                    } else {
                        return notFoundResponse();
                    }
                },
                new Delay(SECONDS, 2)
            );

        // then
        long timeBeforeRequest = System.currentTimeMillis();
        HttpResponse httpResponse = makeRequest(
            request()
                .withPath(calculatePath("object_callback"))
                .withMethod("POST")
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_http"),
            headersToIgnore
        );
        long timeAfterRequest = System.currentTimeMillis();

        // and
        assertEquals(
            response()
                .withStatusCode(ACCEPTED_202.code())
                .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                .withHeaders(
                    header("x-object-callback", "test_object_callback_header")
                )
                .withBody("an_object_callback_response"),
            httpResponse
        );
        assertThat(timeAfterRequest - timeBeforeRequest, greaterThanOrEqualTo(MILLISECONDS.toMillis(1900)));
        assertThat(timeAfterRequest - timeBeforeRequest, lessThanOrEqualTo(SECONDS.toMillis(4)));
    }

    @Test
    public void shouldForwardByObjectCallbackWithDelayViaWebSocket() throws Exception {
        viaWebSocket(() -> {
            // when
            mockServerClient
                .when(
                    request()
                        .withPath(calculatePath("echo"))
                )
                .forward(
                    httpRequest -> request()
                        .withHeader("Host", "localhost:" + (httpRequest.isSecure() ? secureEchoServer.getPort() : insecureEchoServer.getPort()))
                        .withHeader("x-test", httpRequest.getFirstHeader("x-test"))
                        .withBody("some_overridden_body")
                        .withSecure(httpRequest.isSecure()),
                    new Delay(SECONDS, 2)
                );

            // then
            long timeBeforeRequest = System.currentTimeMillis();
            HttpResponse httpResponse = makeRequest(
                request()
                    .withPath(calculatePath("echo"))
                    .withMethod("POST")
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("an_example_body_http"),
                headersToIgnore
            );
            long timeAfterRequest = System.currentTimeMillis();

            // and
            assertEquals(
                response()
                    .withStatusCode(OK_200.code())
                    .withReasonPhrase(OK_200.reasonPhrase())
                    .withHeaders(
                        header("x-test", "test_headers_and_body")
                    )
                    .withBody("some_overridden_body"),
                httpResponse
            );
            assertThat(timeAfterRequest - timeBeforeRequest, greaterThanOrEqualTo(MILLISECONDS.toMillis(1900)));
            assertThat(timeAfterRequest - timeBeforeRequest, lessThanOrEqualTo(SECONDS.toMillis(4)));
        });
    }

    @Test
    public void shouldForwardByObjectCallbackWithDelayViaLocalJVM() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                httpRequest -> request()
                    .withHeader("Host", "localhost:" + (httpRequest.isSecure() ? secureEchoServer.getPort() : insecureEchoServer.getPort()))
                    .withHeader("x-test", httpRequest.getFirstHeader("x-test"))
                    .withBody("some_overridden_body")
                    .withSecure(httpRequest.isSecure()),
                new Delay(SECONDS, 2)
            );

        // then
        long timeBeforeRequest = System.currentTimeMillis();
        HttpResponse httpResponse = makeRequest(
            request()
                .withPath(calculatePath("echo"))
                .withMethod("POST")
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("an_example_body_http"),
            headersToIgnore
        );
        long timeAfterRequest = System.currentTimeMillis();

        // and
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeaders(
                    header("x-test", "test_headers_and_body")
                )
                .withBody("some_overridden_body"),
            httpResponse
        );
        assertThat(timeAfterRequest - timeBeforeRequest, greaterThanOrEqualTo(MILLISECONDS.toMillis(1900)));
        assertThat(timeAfterRequest - timeBeforeRequest, lessThanOrEqualTo(SECONDS.toMillis(4)));
    }
}
