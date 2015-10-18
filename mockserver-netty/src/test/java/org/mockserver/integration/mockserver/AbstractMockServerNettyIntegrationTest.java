package org.mockserver.integration.mockserver;

import com.google.common.base.Charsets;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.integration.server.SameJVMAbstractClientServerIntegrationTest;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.server.TestClasspathTestExpectationCallback;
import org.mockserver.socket.SSLFactory;
import org.mockserver.streams.IOStreamUtils;

import javax.net.ssl.SSLSocket;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public abstract class AbstractMockServerNettyIntegrationTest extends SameJVMAbstractClientServerIntegrationTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    public abstract void startServerAgain();

    @Test
    public void shouldThrowExceptionIfFailToBindToSocket() {
        // given
        System.out.println("--- IGNORE THE FOLLOWING java.net.BindException EXCEPTION ---");
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage(containsString("Exception while starting MockServer"));

        // when
        startServerAgain();
    }


    @Test
    public void shouldReturnResponseWithConnectionOptionsAndKeepAliveFalseAndContentLengthOverride() {
        // given
        List headersToIgnore = new ArrayList(this.headersToIgnore);
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
                        .withHeader(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
                        .withHeader(HttpHeaders.CONNECTION, "close")
                        .withHeader(header(HttpHeaders.CONTENT_LENGTH, "some_long_body".length() / 2))
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody("some_lo"),
                makeRequest(
                        request()
                                .withPath(calculatePath("")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withHeader(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
                        .withHeader(HttpHeaders.CONNECTION, "close")
                        .withHeader(header(HttpHeaders.CONTENT_LENGTH, "some_long_body".length() / 2))
                        .withStatusCode(HttpStatusCode.OK_200.code())
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
        List headersToIgnore = new ArrayList(this.headersToIgnore);
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
                                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.ANY_AUDIO_TYPE.toString())
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
                        .withHeader(HttpHeaders.CONNECTION, "keep-alive")
                        .withHeader(header(HttpHeaders.CONTENT_LENGTH, "some_long_body".length() / 2))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.ANY_AUDIO_TYPE.toString())
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody(binary("some_lo".getBytes())),
                makeRequest(
                        request()
                                .withPath(calculatePath("")),
                        headersToIgnore)
        );
        // - in https
        assertEquals(
                response()
                        .withHeader(HttpHeaders.CONNECTION, "keep-alive")
                        .withHeader(header(HttpHeaders.CONTENT_LENGTH, "some_long_body".length() / 2))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.ANY_AUDIO_TYPE.toString())
                        .withStatusCode(HttpStatusCode.OK_200.code())
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
                                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.ANY_AUDIO_TYPE.toString())
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
                    "GET " + calculatePath("") + " HTTP/1.1\n" +
                    "Content-Length: 0\r\n" +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertThat(IOStreamUtils.readInputStreamToString(socket), is("" +
                            "HTTP/1.1 200 OK\n" +
                            "Content-Type: audio/*\n" +
                            "Connection: close\n"
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
                assertThat(se.getMessage(), anyOf(containsString("Broken pipe"), containsString("Connection reset")));
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
            sslSocket = SSLFactory.getInstance().wrapSocket(new Socket("localhost", getMockServerPort()));
            OutputStream output = sslSocket.getOutputStream();

            // when
            output.write(("" +
                    "GET " + calculatePath("") + " HTTP/1.1\n" +
                    "Content-Length: 0\r\n" +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertThat(IOStreamUtils.readInputStreamToString(sslSocket), is("" +
                            "HTTP/1.1 200 OK\n" +
                            "Content-Type: audio/*\n" +
                            "Connection: close\n"
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
                    "GET " + calculatePath("") + " HTTP/1.1\n" +
                    "Content-Length: 0\r\n" +
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
            sslSocket = SSLFactory.getInstance().wrapSocket(new Socket("localhost", getMockServerPort()));
            OutputStream output = sslSocket.getOutputStream();

            // when
            output.write(("" +
                    "GET " + calculatePath("") + " HTTP/1.1\n" +
                    "Content-Length: 0\r\n" +
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
    public void shouldCallbackToSpecifiedClassInTestClasspath() {
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
                                header("x-callback", "test_callback_header"),
                                header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
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
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(0).getPath(), calculatePath("callback"));

        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withHeaders(
                                header("x-callback", "test_callback_header"),
                                header(HttpHeaders.CONTENT_TYPE, TEXT_PLAIN)
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
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(1).getPath(), calculatePath("callback"));
    }

}
