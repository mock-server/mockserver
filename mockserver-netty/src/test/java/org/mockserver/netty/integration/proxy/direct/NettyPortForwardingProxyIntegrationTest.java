package org.mockserver.netty.integration.proxy.direct;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.echo.http.EchoServer;
import org.mockserver.netty.MockServer;
import org.mockserver.streams.IOStreamUtils;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.test.Assert.assertContains;

/**
 * @author jamesdbloom
 */
public class NettyPortForwardingProxyIntegrationTest {

    private static EchoServer echoServer;
    private static MockServer mockServer;

    @BeforeClass
    public static void setupFixture() {
        echoServer = new EchoServer(false);

        mockServer = new MockServer(echoServer.getPort(), "127.0.0.1", 0);
    }

    @AfterClass
    public static void shutdownFixture() {
        stopQuietly(echoServer);
        stopQuietly(mockServer);
    }

    @Test
    public void shouldForwardRequestsUsingSocketDirectlyHeadersOnly() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {

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
        }
    }

    @Test
    public void shouldForwardRequestsUsingSocketDirectlyHeadersAndBody() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {

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
        }
    }

    @Test
    public void shouldForwardRequestsUsingSocketDirectlyNotFound() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", mockServer.getLocalPort())) {

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
        }
    }
}
