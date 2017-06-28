package org.mockserver.integration.mockserver;

import com.google.common.base.Charsets;
import com.google.common.net.MediaType;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.integration.server.SameJVMAbstractClientServerIntegrationTest;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.server.TestClasspathTestExpectationCallback;
import org.mockserver.socket.KeyStoreFactory;
import org.mockserver.socket.PortFactory;
import org.mockserver.streams.IOStreamUtils;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.ACCEPTED_202;
import static org.mockserver.model.HttpStatusCode.NOT_ACCEPTABLE_406;
import static org.mockserver.model.HttpStatusCode.OK_200;
import static org.mockserver.socket.SSLSocketFactory.sslSocketFactory;

/**
 * @author jamesdbloom
 */
public abstract class AbstractMockServerNettyIntegrationTest extends SameJVMAbstractClientServerIntegrationTest {

    @Test
    public void shouldCallbackToSpecifiedObject() {
        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("object_callback"))
                )
                .callback(
                        new ExpectationCallback() {
                            @Override
                            public HttpResponse handle(HttpRequest httpRequest) {
                                HttpRequest expectation = request()
                                        .withPath(calculatePath("object_callback"))
                                        .withMethod("POST")
                                        .withHeaders(
                                                header("x-test", "test_headers_and_body")
                                        )
                                        .withBody("an_example_body_http");
                                if (new MatcherBuilder().transformsToMatcher(expectation).matches(httpRequest)) {
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

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(ACCEPTED_202.code())
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
    public void shouldCallbackToSpecifiedObjectForVeryLargeRequestAndResponses() {
        int bytes = 65536*10;
        char[] chars = new char[bytes];
        Arrays.fill(chars, 'a');
        final String veryLargeString = new String(chars);

        // when
        mockServerClient
                .when(
                        request()
                                .withPath(calculatePath("object_callback"))
                )
                .callback(
                        new ExpectationCallback() {
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
    public void shouldBindToNewSocket() {
        // given
        int firstNewPort = PortFactory.findFreePort();
        int secondNewPort = PortFactory.findFreePort();
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                        .withBody("{" + NEW_LINE +
                                "  \"ports\" : [ " + getMockServerPort() + " ]" + NEW_LINE +
                                "}"),
                makeRequest(
                        request()
                                .withPath(calculatePath("status"))
                                .withMethod("PUT"),
                        headersToIgnore)
        );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(ACCEPTED_202.code())
                        .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                        .withBody("{" + NEW_LINE +
                                "  \"ports\" : [ " + firstNewPort + " ]" + NEW_LINE +
                                "}"),
                makeRequest(
                        request()
                                .withPath(calculatePath("bind"))
                                .withMethod("PUT")
                                .withBody("{" + NEW_LINE +
                                        "  \"ports\" : [ " + firstNewPort + " ]" + NEW_LINE +
                                        "}"),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                        .withBody("{" + NEW_LINE +
                                "  \"ports\" : [ " + getMockServerPort() + ", " + firstNewPort + " ]" + NEW_LINE +
                                "}"),
                makeRequest(
                        request()
                                .withPath(calculatePath("status"))
                                .withMethod("PUT"),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withStatusCode(ACCEPTED_202.code())
                        .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                        .withBody("{" + NEW_LINE +
                                "  \"ports\" : [ " + secondNewPort + " ]" + NEW_LINE +
                                "}"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("bind"))
                                .withMethod("PUT")
                                .withBody("{" + NEW_LINE +
                                        "  \"ports\" : [ " + secondNewPort + " ]" + NEW_LINE +
                                        "}"),
                        headersToIgnore)
        );
        assertEquals(
                response()
                        .withStatusCode(OK_200.code())
                        .withHeader(CONTENT_TYPE.toString(), "application/json; charset=utf-8")
                        .withBody("{" + NEW_LINE +
                                "  \"ports\" : [ " + getMockServerSecurePort() + ", " + firstNewPort + ", " + secondNewPort + " ]" + NEW_LINE +
                                "}"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("status"))
                                .withMethod("PUT")
                                .withBody("{" + NEW_LINE +
                                        "  \"ports\" : [ " + firstNewPort + " ]" + NEW_LINE +
                                        "}"),
                        headersToIgnore)
        );
    }

    @Test
    public void shouldErrorWhenBindingToUnavailableSocket() throws InterruptedException, IOException {
        System.out.println("\n\n--- IGNORE THE FOLLOWING java.net.BindException EXCEPTION ---\n\n");
        ServerSocket server = null;
        try {
            // given
            server = new ServerSocket(0);
            int newPort = server.getLocalPort();

            // then
            // - in http
            assertEquals(
                    response()
                            .withStatusCode(NOT_ACCEPTABLE_406.code())
                            .withHeader(CONTENT_TYPE.toString(), "text/plain; charset=utf-8")
                            .withBody("Exception while binding MockServer to port " + newPort + " port already in use"),
                    makeRequest(
                            request()
                                    .withPath(calculatePath("bind"))
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
                        .withBody("some_lo"),
                makeRequest(
                        request()
                                .withSecure(true)
                                .withPath(calculatePath("")),
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
                                .withBody(binary("some_long_body".getBytes()))
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
                        .withBody(binary("some_lo".getBytes())),
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
                        .withBody(binary("some_lo".getBytes())),
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
                                .withBody(binary("some_long_body".getBytes()))
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
            socket = new Socket("localhost", getMockServerPort());
            OutputStream output = socket.getOutputStream();

            // when
            output.write(("" +
                    "GET " + calculatePath("") + " HTTP/1.1" + NEW_LINE +
                    "Content-Length: 0\r" + NEW_LINE +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertThat(IOStreamUtils.readInputStreamToString(socket), is("" +
                    "HTTP/1.1 200 OK" + NEW_LINE +
                    "content-type: audio/*" + NEW_LINE +
                    "connection: close\n"
            ));

            TimeUnit.SECONDS.sleep(3);

            // and - socket is closed
            try {
                // flush data to increase chance that Java / OS notice socket has been closed
                output.write("some_random_bytes".getBytes(Charsets.UTF_8));
                output.flush();
                output.write("some_random_bytes".getBytes(Charsets.UTF_8));
                output.flush();

                TimeUnit.SECONDS.sleep(2);

                IOStreamUtils.readInputStreamToString(socket);
                fail("Expected socket read to fail because the socket was closed / reset");
            } catch (SocketException se) {
                assertThat(se.getMessage(), anyOf(containsString("Broken pipe"), containsString("Connection reset"), containsString("Protocol wrong type")));
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
            sslSocket = sslSocketFactory().wrapSocket(new Socket("localhost", getMockServerPort()));
            OutputStream output = sslSocket.getOutputStream();

            // when
            output.write(("" +
                    "GET " + calculatePath("") + " HTTP/1.1" + NEW_LINE +
                    "Content-Length: 0\r" + NEW_LINE +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertThat(IOStreamUtils.readInputStreamToString(sslSocket), is("" +
                    "HTTP/1.1 200 OK" + NEW_LINE +
                    "content-type: audio/*" + NEW_LINE +
                    "connection: close\n"
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
                                .withResponseBytes("some_random_bytes".getBytes())
                );

        // then
        // - in http
        Socket socket = null;
        try {
            // given
            socket = new Socket("localhost", getMockServerPort());
            OutputStream output = socket.getOutputStream();

            // when
            output.write(("" +
                    "GET " + calculatePath("") + " HTTP/1.1" + NEW_LINE +
                    "Content-Length: 0\r" + NEW_LINE +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertThat(IOUtils.toString(socket.getInputStream(), Charsets.UTF_8.name()), is("some_random_bytes"));
        } finally {
            if (socket != null) {
                socket.close();
            }
        }

        // and
        // - in https
        SSLSocket sslSocket = null;
        try {
            sslSocket = sslSocketFactory().wrapSocket(new Socket("localhost", getMockServerPort()));
            OutputStream output = sslSocket.getOutputStream();

            // when
            output.write(("" +
                    "GET " + calculatePath("") + " HTTP/1.1" + NEW_LINE +
                    "Content-Length: 0\r" + NEW_LINE +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertThat(IOUtils.toString(sslSocket.getInputStream(), Charsets.UTF_8.name()), is("some_random_bytes"));
        } finally {
            if (sslSocket != null) {
                sslSocket.close();
            }
        }
    }

    @Test
    @SuppressWarnings("Duplicates")
    public void shouldCallbackToSpecifiedClassInTestClasspath() {
        // given
        TestClasspathTestExpectationCallback.httpRequests.clear();
        TestClasspathTestExpectationCallback.httpResponse = response()
                .withStatusCode(ACCEPTED_202.code())
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
                        .withStatusCode(ACCEPTED_202.code())
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
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(0).getBody().getValue(), "an_example_body_http");
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(0).getPath().getValue(), calculatePath("callback"));

        // - in https
        assertEquals(
                response()
                        .withStatusCode(ACCEPTED_202.code())
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
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(1).getBody().getValue(), "an_example_body_https");
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(1).getPath().getValue(), calculatePath("callback"));
    }

}
