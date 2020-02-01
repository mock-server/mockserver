package org.mockserver.netty.integration.proxy.direct;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.netty.MockServer;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.mockserver.streams.IOStreamUtils;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.socket.tls.SSLSocketFactory.sslSocketFactory;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.Assert.assertContains;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class NettyPortForwardingSecureProxyIntegrationTest {

    private static EchoServer echoServer;
    private static MockServer mockServer;
    private static MockServerClient mockServerClient;

    @BeforeClass
    public static void setupFixture() {
        echoServer = new EchoServer(true);

        mockServer = new MockServer(echoServer.getPort(), "127.0.0.1");

        mockServerClient = new MockServerClient("127.0.0.1", mockServer.getLocalPort());
    }

    @AfterClass
    public static void shutdownFixture() {
        stopQuietly(echoServer);
        stopQuietly(mockServer);
        stopQuietly(mockServerClient);
    }

    @Test
    public void shouldForwardRequestsUsingSocketDirectlyHeadersOnly() throws Exception {
        Socket socket = null;
        try {
            socket = sslSocketFactory().wrapSocket(new Socket("127.0.0.1", mockServer.getLocalPort()));

            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send GET request for headers only
            output.write(("" +
                "GET /test_headers_only HTTP/1.1\r\n" +
                "Host: 127.0.0.1:" + echoServer.getPort() + "\r\n" +
                "X-Test: test_headers_only\r\n" +
                "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "X-Test: test_headers_only");

            // and
            mockServerClient.verify(
                request()
                    .withMethod("GET")
                    .withPath("/test_headers_only"),
                exactly(1)
            );
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Test
    public void shouldForwardRequestsUsingSocketDirectlyHeadersAndBody() throws Exception {
        Socket socket = null;
        try {

            socket = sslSocketFactory().wrapSocket(new Socket("127.0.0.1", mockServer.getLocalPort()));

            // given
            OutputStream output = socket.getOutputStream();

            // - send GET request for headers and body
            output.write(("" +
                "GET /test_headers_and_body HTTP/1.1\r\n" +
                "Host: 127.0.0.1:" + echoServer.getPort() + "\r\n" +
                "X-Test: test_headers_and_body\r\n" +
                "Content-Length:" + "an_example_body".getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                "\r\n" +
                "an_example_body" + "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            String response = IOStreamUtils.readInputStreamToString(socket);
            assertContains(response, "X-Test: test_headers_and_body");
            assertContains(response, "an_example_body");

            // and
            mockServerClient.verify(
                request()
                    .withMethod("GET")
                    .withPath("/test_headers_and_body")
                    .withBody("an_example_body"),
                exactly(1)
            );
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Test
    public void shouldForwardRequestsUsingSocketDirectlyNotFound() throws Exception {
        Socket socket = null;
        try {

            socket = sslSocketFactory().wrapSocket(new Socket("127.0.0.1", mockServer.getLocalPort()));

            // given
            OutputStream output = socket.getOutputStream();

            // - send GET request for headers and body
            output.write(("" +
                "GET /not_found HTTP/1.1\r\n" +
                "Host: 127.0.0.1:" + echoServer.getPort() + "\r\n" +
                "\r\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 404 Not Found");

            // and
            mockServerClient.verify(
                request()
                    .withMethod("GET")
                    .withPath("/not_found"),
                exactly(1)
            );
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }
}
