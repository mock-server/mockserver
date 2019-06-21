package org.mockserver.integration.mocking;

import com.google.common.net.MediaType;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.integration.server.AbstractExtendedSameJVMMockingIntegrationTest;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.server.TestClasspathTestExpectationResponseCallback;
import org.mockserver.socket.PortFactory;
import org.mockserver.streams.IOStreamUtils;
import org.mockserver.verify.VerificationTimes;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.*;
import static org.mockserver.socket.tls.SSLSocketFactory.sslSocketFactory;

/**
 * @author jamesdbloom
 */
public abstract class AbstractExtendedNettyMockingIntegrationTest extends AbstractExtendedSameJVMMockingIntegrationTest {

    @Test
    public void shouldRespondByObjectCallback() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("object_callback"))
            )
            .respond(
                new ExpectationResponseCallback() {
                    @Override
                    public HttpResponse handle(HttpRequest httpRequest) {
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

    @Test
    public void shouldRespondByObjectCallbackAndVerifyRequests() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("object_callback")),
                exactly(1)
            )
            .respond(
                new ExpectationResponseCallback() {
                    @Override
                    public HttpResponse handle(HttpRequest httpRequest) {
                        return response()
                            .withStatusCode(ACCEPTED_202.code())
                            .withReasonPhrase(ACCEPTED_202.reasonPhrase())
                            .withBody("an_object_callback_response");
                    }
                }
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
    public void shouldRespondByObjectCallbackForVeryLargeRequestAndResponses() {
        int bytes = 65536 * 10;
        char[] chars = new char[bytes];
        Arrays.fill(chars, 'a');
        final String veryLargeString = new String(chars);

        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("object_callback"))
            )
            .respond(
                new ExpectationResponseCallback() {
                    @Override
                    public HttpResponse handle(HttpRequest httpRequest) {
                        return response()
                            .withBody(veryLargeString);
                    }
                }
            );

        // then
        // - in http
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

        // - in https
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withBody(veryLargeString),
            makeRequest(
                request()
                    .withSecure(true)
                    .withPath(calculatePath("object_callback"))
                    .withMethod("POST")
                    .withBody(veryLargeString),
                headersToIgnore
            )
        );
    }

    @Test
    public void shouldForwardByObjectCallback() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                new ExpectationForwardCallback() {
                    @Override
                    public HttpRequest handle(HttpRequest httpRequest) {
                        return request()
                            .withHeader("Host", "localhost:" + (httpRequest.isSecure() ? secureEchoServer.getPort() : insecureEchoServer.getPort()))
                            .withHeader("x-test", httpRequest.getFirstHeader("x-test"))
                            .withBody("some_overridden_body")
                            .withSecure(httpRequest.isSecure());
                    }
                }
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
    public void shouldBindToNewSocketAndReturnStatus() {
        // given
        int firstNewPort = PortFactory.findFreePort();
        int secondNewPort = PortFactory.findFreePort();
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody("{" + NEW_LINE +
                    "  \"ports\" : [ " + getServerPort() + " ]" + NEW_LINE +
                    "}", MediaType.JSON_UTF_8),
            makeRequest(
                request()
                    .withPath(calculatePath("mockserver/status"))
                    .withMethod("PUT"),
                headersToIgnore)
        );

        // then
        // - in http
        assertEquals(
            response()
                .withStatusCode(OK_200.code())
                .withReasonPhrase(OK_200.reasonPhrase())
                .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                .withBody("{" + NEW_LINE +
                    "  \"ports\" : [ " + firstNewPort + " ]" + NEW_LINE +
                    "}", MediaType.JSON_UTF_8),
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
                .withBody("{" + NEW_LINE +
                    "  \"ports\" : [ " + this.getServerPort() + ", " + firstNewPort + " ]" + NEW_LINE +
                    "}", MediaType.JSON_UTF_8),
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
                .withBody("{" + NEW_LINE +
                    "  \"ports\" : [ " + secondNewPort + " ]" + NEW_LINE +
                    "}", MediaType.JSON_UTF_8),
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
                .withBody("{" + NEW_LINE +
                    "  \"ports\" : [ " + getServerSecurePort() + ", " + firstNewPort + ", " + secondNewPort + " ]" + NEW_LINE +
                    "}", MediaType.JSON_UTF_8),
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
        System.out.println(NEW_LINE + NEW_LINE + "+++ IGNORE THE FOLLOWING java.net.BindException EXCEPTION +++" + NEW_LINE + NEW_LINE);
        ServerSocket server = null;
        try {
            // given
            server = new ServerSocket(0);
            int newPort = server.getLocalPort();

            // then
            // - in http
            assertEquals(
                response()
                    .withStatusCode(BAD_REQUEST_400.code())
                    .withReasonPhrase(BAD_REQUEST_400.reasonPhrase())
                    .withHeader(CONTENT_TYPE.toString(), "text/plain; charset=utf-8")
                    .withBody("Exception while binding MockServer to port " + newPort + " port already in use", MediaType.PLAIN_TEXT_UTF_8),
                makeRequest(
                    request()
                        .withPath(calculatePath("mockserver/bind"))
                        .withMethod("PUT")
                        .withBody("{" + NEW_LINE +
                            "  \"ports\" : [ " + newPort + " ]" + NEW_LINE +
                            "}"),
                    headersToIgnore)
            );

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
        List<String> headersToIgnore = new ArrayList<String>(this.headersToIgnore);
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
    public void shouldReturnResponseByMatchingVeryLargeHeader() {
        // when
        ConfigurationProperties.logLevel("DEBUG");
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
        List<String> headersToIgnore = new ArrayList<String>(this.headersToIgnore);
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
                .withBody(binary("some_lo".getBytes(UTF_8))),
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
                .withBody(binary("some_lo".getBytes(UTF_8))),
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
        Socket socket = null;
        try {
            // given
            socket = new Socket("localhost", this.getServerPort());
            OutputStream output = socket.getOutputStream();

            // when
            output.write(("" +
                "GET " + calculatePath("") + " HTTP/1.1" + NEW_LINE +
                "Content-Length: 0" + NEW_LINE +
                NEW_LINE
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertThat(IOStreamUtils.readInputStreamToString(socket), is("" +
                "HTTP/1.1 200 OK" + NEW_LINE +
                "content-type: audio/*" + NEW_LINE +
                "connection: close" + NEW_LINE
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
        } finally {
            if (socket != null) {
                socket.close();
            }
        }

        // and
        // - in https
        SSLSocket sslSocket = null;
        try {
            sslSocket = sslSocketFactory().wrapSocket(new Socket("localhost", this.getServerPort()));
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
                "connection: close" + NEW_LINE
            ));
        } finally {
            if (sslSocket != null) {
                sslSocket.close();
            }
        }
    }

    @Test
    public void shouldReturnErrorResponseForExpectationWithHttpError() throws Exception {
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
        Socket socket = null;
        try {
            // given
            socket = new Socket("localhost", this.getServerPort());
            OutputStream output = socket.getOutputStream();

            // when
            output.write(("" +
                "GET " + calculatePath("") + " HTTP/1.1" + NEW_LINE +
                "Content-Length: 0" + NEW_LINE +
                NEW_LINE
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertThat(IOUtils.toString(socket.getInputStream(), StandardCharsets.UTF_8.name()), is("some_random_bytes"));
        } finally {
            if (socket != null) {
                socket.close();
            }
        }

        // and
        // - in https
        SSLSocket sslSocket = null;
        try {
            sslSocket = sslSocketFactory().wrapSocket(new Socket("localhost", this.getServerPort()));
            OutputStream output = sslSocket.getOutputStream();

            // when
            output.write(("" +
                "GET " + calculatePath("") + " HTTP/1.1" + NEW_LINE +
                "Content-Length: 0" + NEW_LINE +
                NEW_LINE
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertThat(IOUtils.toString(sslSocket.getInputStream(), StandardCharsets.UTF_8.name()), is("some_random_bytes"));
        } finally {
            if (sslSocket != null) {
                sslSocket.close();
            }
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
        Socket socket = null;
        try {
            // given
            socket = new Socket("localhost", this.getServerPort());
            OutputStream output = socket.getOutputStream();

            // when
            output.write(("" +
                "GET " + calculatePath("http_error") + " HTTP/1.1" + NEW_LINE +
                "Content-Length: 0" + NEW_LINE +
                NEW_LINE
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertThat(IOUtils.toString(socket.getInputStream(), StandardCharsets.UTF_8.name()), is("some_random_bytes"));
        } finally {
            if (socket != null) {
                socket.close();
            }
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
    public void shouldCallbackToSpecifiedClassInTestClasspath() {
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
                    .withCallbackClass("org.mockserver.server.TestClasspathTestExpectationResponseCallback")
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
    public void shouldRespondByObjectCallbackWithDelay() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("object_callback"))
            )
            .respond(
                new ExpectationResponseCallback() {
                    @Override
                    public HttpResponse handle(HttpRequest httpRequest) {
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
                },
                new Delay(SECONDS, 3)
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
        assertThat(timeAfterRequest - timeBeforeRequest, greaterThanOrEqualTo(SECONDS.toMillis(3)));
    }

    @Test
    public void shouldForwardByObjectCallbackWithDelay() {
        // when
        mockServerClient
            .when(
                request()
                    .withPath(calculatePath("echo"))
            )
            .forward(
                new ExpectationForwardCallback() {
                    @Override
                    public HttpRequest handle(HttpRequest httpRequest) {
                        return request()
                            .withHeader("Host", "localhost:" + (httpRequest.isSecure() ? secureEchoServer.getPort() : insecureEchoServer.getPort()))
                            .withHeader("x-test", httpRequest.getFirstHeader("x-test"))
                            .withBody("some_overridden_body")
                            .withSecure(httpRequest.isSecure());
                    }
                },
                new Delay(SECONDS, 3)
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
        assertThat(timeAfterRequest - timeBeforeRequest, greaterThanOrEqualTo(SECONDS.toMillis(3)));
    }
}
