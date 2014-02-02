package org.mockserver.proxy.direct;

import org.apache.commons.io.Charsets;
import org.junit.*;
import org.mockserver.client.proxy.ProxyClient;
import org.mockserver.helloworld.HttpHelloWorldServer;
import org.mockserver.netty.proxy.direct.DirectProxy;
import org.mockserver.netty.proxy.http.HttpProxy;
import org.mockserver.socket.PortFactory;
import org.mockserver.socket.SSLFactory;
import org.mockserver.streams.IOStreamUtils;

import java.io.OutputStream;
import java.net.Socket;

import static org.mockserver.test.Assert.assertContains;

/**
 * @author jamesdbloom
 */
public class NettyDirectSecureProxyIntegrationTest {

    private final static int SERVER_HTTP_PORT = PortFactory.findFreePort();
    private final static int SERVER_HTTPS_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTP_PORT = PortFactory.findFreePort();
    private final static int PROXY_HTTPS_PORT = PortFactory.findFreePort();
    private final static int PROXY_SOCKS_PORT = PortFactory.findFreePort();
    private final static int PROXY_DIRECT_PORT = PortFactory.findFreePort();
    private final static int PROXY_DIRECT_SECURE_PORT = PortFactory.findFreePort();
    private static HttpHelloWorldServer httpHelloWorldServer;
    private static HttpProxy httpProxy;

    @BeforeClass
    public static void setupFixture() throws Exception {
        // start server
        httpHelloWorldServer = new HttpHelloWorldServer(SERVER_HTTP_PORT, SERVER_HTTPS_PORT);

        // start proxy
        httpProxy = new HttpProxy(PROXY_HTTP_PORT, PROXY_HTTPS_PORT, PROXY_SOCKS_PORT, PROXY_DIRECT_PORT, PROXY_DIRECT_SECURE_PORT, "127.0.0.1", SERVER_HTTPS_PORT);
    }

    @AfterClass
    public static void shutdownFixture() {
        // stop server
        httpHelloWorldServer.stop();

        // stop proxy
        httpProxy.stop();
    }

    @Test
    public void shouldForwardRequestsUsingSocketDirectlyHeadersOnly() throws Exception {
        Socket socket = null;
        try {
            socket = SSLFactory.wrapSocket(new Socket("localhost", PROXY_DIRECT_SECURE_PORT));

            // given
            OutputStream output = socket.getOutputStream();

            // when
            // - send GET request for headers only
            output.write(("" +
                    "GET /test_headers_only HTTP/1.1\r\n" +
                    "Host: localhost:" + SERVER_HTTP_PORT + "\r\n" +
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

            socket = SSLFactory.wrapSocket(new Socket("localhost", PROXY_DIRECT_SECURE_PORT));

            // given
            OutputStream output = socket.getOutputStream();

            // - send GET request for headers and body
            output.write(("" +
                    "GET /test_headers_and_body HTTP/1.1\r\n" +
                    "Host: localhost:" + SERVER_HTTP_PORT + "\r\n" +
                    "\r\n"
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

            socket = SSLFactory.wrapSocket(new Socket("localhost", PROXY_DIRECT_SECURE_PORT));

            // given
            OutputStream output = socket.getOutputStream();

            // - send GET request for headers and body
            output.write(("" +
                    "GET /unknown HTTP/1.1\r\n" +
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
