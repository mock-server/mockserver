package org.mockserver.proxy.direct;

import com.google.common.base.Charsets;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.echo.EchoServer;
import org.mockserver.proxy.Proxy;
import org.mockserver.proxy.ProxyBuilder;
import org.mockserver.socket.PortFactory;
import org.mockserver.streams.IOStreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.Socket;

import static org.mockserver.test.Assert.assertContains;

/**
 * @author jamesdbloom
 */
public class NettyDirectProxyIntegrationTest {

    private final static Logger logger = LoggerFactory.getLogger(NettyDirectProxyIntegrationTest.class);

    private final static Integer SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static Integer PROXY_DIRECT_PORT = PortFactory.findFreePort();
    private static EchoServer echoServer;
    private static Proxy httpProxy;

    @BeforeClass
    public static void setupFixture() throws Exception {
        logger.debug("SERVER_HTTP_PORT = " + SERVER_HTTP_PORT);
        logger.debug("PROXY_DIRECT_PORT = " + PROXY_DIRECT_PORT);

        // start echo server
        echoServer = new EchoServer(SERVER_HTTP_PORT);

        // start proxy
        httpProxy = new ProxyBuilder()
                .withLocalPort(PROXY_DIRECT_PORT)
                .withDirect("127.0.0.1", SERVER_HTTP_PORT)
                .build();
    }

    @AfterClass
    public static void shutdownFixture() {
        // stop echo server
        echoServer.stop();

        // stop proxy
        httpProxy.stop();
    }

    @Test
    public void shouldForwardRequestsUsingSocketDirectlyHeadersOnly() throws Exception {
        Socket socket = null;
        try {
            socket = new Socket("localhost", PROXY_DIRECT_PORT);

            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send GET request for headers only
            output.write(("" +
                    "GET /test_headers_only HTTP/1.1\r\n" +
                    "Host: localhost:" + SERVER_HTTP_PORT + "\r\n" +
                    "X-Test: test_headers_only\r\n" +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "X-Test: test_headers_only");
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

            socket = new Socket("localhost", PROXY_DIRECT_PORT);

            // given
            OutputStream output = socket.getOutputStream();

            // - send GET request for headers and body
            output.write(("" +
                    "GET /test_headers_and_body HTTP/1.1\r\n" +
                    "Host: localhost:" + SERVER_HTTP_PORT + "\r\n" +
                    "X-Test: test_headers_and_body\r\n" +
                    "Content-Length:" + "an_example_body".getBytes(Charsets.UTF_8).length + "\r\n" +
                    "\r\n" +
                    "an_example_body" + "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            String response = IOStreamUtils.readInputStreamToString(socket);
            assertContains(response, "X-Test: test_headers_and_body");
            assertContains(response, "an_example_body");
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

            socket = new Socket("localhost", PROXY_DIRECT_PORT);

            // given
            OutputStream output = socket.getOutputStream();

            // - send GET request for headers and body
            output.write(("" +
                    "GET /not_found HTTP/1.1\r\n" +
                    "Host: localhost:" + SERVER_HTTP_PORT + "\r\n" +
                    "\r\n"
            ).getBytes(Charsets.UTF_8));
            output.flush();

            // then
            assertContains(IOStreamUtils.readInputStreamToString(socket), "HTTP/1.1 404 Not Found");
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }
}
